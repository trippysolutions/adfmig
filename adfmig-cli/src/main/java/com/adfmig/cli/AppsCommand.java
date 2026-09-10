package com.adfmig.cli;

import com.adfmig.core.estate.AdfEstate;
import com.adfmig.core.estate.CredentialFinding;
import com.adfmig.core.estate.DatabaseConnection;
import com.adfmig.core.estate.DiscoveredApplication;
import com.adfmig.core.estate.ProjectDependency;
import com.adfmig.parser.EstateParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Surveys a directory tree holding one or more ADF applications and reports what migrating the
 * estate would involve.
 */
@Command(
        name = "apps",
        mixinStandardHelpOptions = true,
        description = "Find the ADF applications under a path: how each is consumed, what it "
                + "connects to, and in what order they can be migrated.")
public final class AppsCommand implements Callable<Integer> {

    @Parameters(paramLabel = "PATH", arity = "1", description = "Directory holding one or more ADF applications.")
    private Path root;

    @Option(names = {"-o", "--json"}, description = "Write the estate survey to this file.")
    private Path jsonOut;


    @Override
    public Integer call() throws Exception {

        if (!Files.isDirectory(root)) {
            System.err.println("not a directory: " + root);
            return 2;
        }

        PrintStream out = System.out;

        AdfEstate estate;
        try (var progress = Terminal.Progress.on(out, "Surveying " + root)) {
            estate = new EstateParser().parse(root, progress::update);
            progress.done(describe(estate));
        }

        applications(out, estate);
        dependencies(out, estate);
        schemas(out, estate);
        credentials(out, estate);
        order(out, estate);

        if (jsonOut != null) {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            if (jsonOut.getParent() != null) Files.createDirectories(jsonOut.getParent());
            mapper.writeValue(jsonOut.toFile(), estate);
            out.printf("%nWrote %s%n", jsonOut.toAbsolutePath());
        }
        return 0;
    }

    private static String describe(AdfEstate estate) {
        long rest = estate.applications().stream()
                .filter(a -> a.profile() == DiscoveredApplication.Profile.REST_CONTRACT)
                .count();
        return String.format("%d application(s), %d already publishing REST",
                estate.applications().size(), rest);
    }

    private void applications(PrintStream out, AdfEstate estate) {
        out.println();
        out.println("=".repeat(104));
        out.printf("%d application(s) under %s%n", estate.applications().size(), estate.root());
        out.println("=".repeat(104));

        out.println();
        // Identify applications by path, not name: several applications in one estate routinely
        // share a name, and the path is what the user needs to pass to `adfmig analyze`.
        out.printf("  %-46s %5s %5s %5s %5s  %s%n", "APPLICATION", "EO", "VO", "AM", "REST", "PROFILE");
        out.printf("  %s%n", "-".repeat(102));
        for (DiscoveredApplication app : estate.applications()) {
            out.printf("  %-46s %5d %5d %5d %5d  %s%n",
                    truncateLeft(app.path(), 46),
                    app.entityObjects(), app.viewObjects(), app.applicationModules(), app.restResources(),
                    app.profile().description());
        }

        out.println();
        out.println("  BY PROFILE");
        Map<DiscoveredApplication.Profile, List<DiscoveredApplication>> byProfile = estate.byProfile();
        byProfile.forEach((profile, apps) ->
                out.printf("      %-52s %4d%n", profile.description(), apps.size()));

        List<DiscoveredApplication> rest = byProfile.getOrDefault(
                DiscoveredApplication.Profile.REST_CONTRACT, List.of());
        if (!rest.isEmpty()) {
            out.println();
            out.println("      Start with the applications that already publish REST. Their contract,");
            out.println("      operations and security grants are declared, so the migration preserves");
            out.println("      the contract and can be verified response by response.");
        }
    }

    private void dependencies(PrintStream out, AdfEstate estate) {
        List<ProjectDependency> cross = estate.crossApplicationDependencies();
        out.println();
        out.println("  DEPENDENCIES BETWEEN APPLICATIONS");
        if (cross.isEmpty()) {
            out.println("      None found. Each application can be migrated independently.");
            return;
        }
        out.printf("      %d dependency reaching across an application boundary — shared business%n",
                cross.size());
        out.println("      components. Migrate these into one shared module, not a copy per application:");
        cross.stream().limit(20).forEach(d ->
                out.printf("        %s  ->  %s%n", d.fromApplication(), d.toApplication()));
        if (cross.size() > 20) out.printf("        ... and %d more%n", cross.size() - 20);
    }

    private void schemas(PrintStream out, AdfEstate estate) {
        out.println();
        out.println("  DATABASE");

        Map<String, List<String>> conflicts = estate.schemaConflicts();
        if (conflicts.isEmpty()) {
            long connected = estate.connectionsByApplication().values().stream()
                    .filter(c -> !c.isEmpty()).count();
            out.printf("      %d application(s) declare a database connection; no shared schema found.%n",
                    connected);
            return;
        }

        out.printf("      %d schema(s) are written by more than one application.%n", conflicts.size());
        out.println();
        out.println("      This is the most expensive thing to discover late. Migrating one of these");
        out.println("      applications to JPA while its neighbour stays on ADF leaves two writers on");
        out.println("      the same rows using different optimistic locking — ADF's change indicator");
        out.println("      against Hibernate's version column. Neither sees the other's writes, so");
        out.println("      updates are lost silently in production.");
        out.println();
        out.println("      Migrate these together, or agree a locking strategy before either moves.");
        out.println();
        out.println("      Datasource entries match on JNDI name. Two applications naming the same");
        out.println("      datasource almost always share a schema, but confirming it means checking");
        out.println("      the WebLogic domain, which is outside what this tool can see.");

        List<String> optimistic = estate.applicationsUsingOptimisticLocking();
        if (!optimistic.isEmpty()) {
            out.println();
            out.printf("      %d application(s) declare optimistic locking. The generated entities%n",
                    optimistic.size());
            out.println("      must keep enforcing it with a version column, or concurrent updates");
            out.println("      begin overwriting one another after the migration.");
        }
        out.println();
        conflicts.forEach((identity, apps) -> {
            out.printf("        %s%n", identity);
            apps.forEach(a -> out.printf("            %s%n", a));
        });
    }

    /**
     * Reports credential material committed to source, by location only. Values are never read;
     * see {@link CredentialFinding}.
     */
    private void credentials(PrintStream out, AdfEstate estate) {
        List<CredentialFinding> findings = estate.credentialFindings();
        out.println();
        out.println("  CREDENTIALS IN SOURCE");
        if (findings.isEmpty()) {
            out.println("      None found.");
            return;
        }

        Map<CredentialFinding.Kind, Long> byKind = findings.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CredentialFinding::kind, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        byKind.forEach((kind, count) ->
                out.printf("      %-52s %4d file(s)%n", kind.description(), count));

        out.println();
        out.println("      Oracle's {903} and {904} prefixes are obfuscation, not encryption: they");
        out.println("      reverse with Oracle's own libraries, so anyone who can read the file can");
        out.println("      read the password. Treat these as plaintext secrets in source control.");
        out.println("      Rotate them; do not carry them into the migrated application.");
        out.println();
        out.println("      This tool records locations only and never reads a secret's value.");
        out.println();
        findings.stream().limit(8).forEach(f ->
                out.printf("        %-62s %s%n", truncateLeft(f.path(), 62), f.location()));
        if (findings.size() > 8) out.printf("        ... and %d more%n", findings.size() - 8);
    }

    private void order(PrintStream out, AdfEstate estate) {
        if (estate.crossApplicationDependencies().isEmpty()) return;

        out.println();
        out.println("  MIGRATION ORDER (dependencies first)");
        List<DiscoveredApplication> ordered = estate.migrationOrder();
        for (int i = 0; i < ordered.size(); i++) {
            out.printf("      %2d. %-46s %s%n", i + 1,
                    truncateLeft(ordered.get(i).path(), 46), ordered.get(i).profile().description());
        }

        List<DiscoveredApplication> cycles = estate.dependencyCycles();
        if (!cycles.isEmpty()) {
            out.printf("%n      %d application(s) depend on each other and cannot be ordered%n", cycles.size());
            out.println("      automatically. The cycle has to be broken deliberately:");
            cycles.forEach(a -> out.printf("        %s%n", a.path()));
        }
    }

    /** Keeps the tail of a path, which is the part that distinguishes one application from another. */
    private static String truncateLeft(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : "..." + s.substring(s.length() - (max - 3));
    }
}
