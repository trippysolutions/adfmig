package com.adfmig.cli;

import com.adfmig.core.AdfArtifactType;
import com.adfmig.core.ScanResult;
import com.adfmig.parser.ProjectScanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Inventories one or more ADF applications and reports what each contains.
 */
@Command(
        name = "scan",
        mixinStandardHelpOptions = true,
        description = "Inventory an ADF application: what artifacts it contains and what they mean for a migration.")
public final class ScanCommand implements Callable<Integer> {

    @Parameters(paramLabel = "PATH", arity = "1..*",
            description = "Root directory of an ADF application. Repeatable.")
    private List<Path> roots;

    @Option(names = {"-o", "--json"},
            description = "Write the full machine-readable scan result to this file.")
    private Path jsonOut;

    @Option(names = "--show-ignored",
            description = "Include artifact types that are irrelevant to a migration.")
    private boolean showIgnored;


    @Override
    public Integer call() throws Exception {

        PrintStream out = System.out;
        ProjectScanner scanner = new ProjectScanner();
        List<ScanResult> results = new ArrayList<>();

        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                System.err.println("not a directory: " + root);
                return 2;
            }
            ScanResult result = scanner.scan(root);
            results.add(result);
            print(out, result);
        }

        if (jsonOut != null) {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            Object payload = results.size() == 1 ? results.get(0) : results;
            if (jsonOut.getParent() != null) Files.createDirectories(jsonOut.getParent());
            mapper.writeValue(jsonOut.toFile(), payload);
            out.printf("%nWrote %s%n", jsonOut.toAbsolutePath());
        }
        return 0;
    }

    private void print(PrintStream out, ScanResult r) {
        out.println();
        out.println("=".repeat(78));
        out.println(Path.of(r.root()).getFileName());
        out.println("=".repeat(78));
        out.printf("  %s%n", r.root());
        out.printf("  %,d files walked, %,d artifacts, %,d ms%n",
                r.filesVisited(), r.artifacts().size(), r.durationMs());

        printVerdict(out, r);
        printByLayer(out, r);
        printRelevance(out, r);

        if (!r.unparseable().isEmpty()) {
            out.printf("%n  Unreadable XML (%d) — each one is an unmigratable artifact:%n",
                    r.unparseable().size());
            r.unparseable().entrySet().stream().limit(10).forEach(e ->
                    out.printf("    %-52s %s%n", truncate(e.getKey(), 52), e.getValue()));
            if (r.unparseable().size() > 10) {
                out.printf("    ... and %d more%n", r.unparseable().size() - 10);
            }
        }
    }

    /**
     * The single most consequential thing a scan can tell a customer: whether this application can
     * be migrated behind an existing contract, or whether its consumers have to be rewritten too.
     */
    private void printVerdict(PrintStream out, ScanResult r) {
        out.println();
        out.println("  MIGRATION PROFILE");
        if (r.hasExistingRestContract()) {
            out.printf("    + Already exposes REST (%d resources, %d registries).%n",
                    r.count(AdfArtifactType.REST_RESOURCE),
                    r.count(AdfArtifactType.REST_RESOURCE_REGISTRY));
            out.println("      URL structure, operation signatures and security grants are declared,");
            out.println("      so the migration is contract-preserving and response-by-response verifiable.");
        }
        if (r.hasAdfFacesConsumers()) {
            out.printf("    ! Bound to an ADF Faces UI (%d page definitions, %d pages, %d fragments).%n",
                    r.count(AdfArtifactType.PAGE_DEFINITION),
                    r.count(AdfArtifactType.JSF_PAGE),
                    r.count(AdfArtifactType.JSF_FRAGMENT));
            out.println("      ADF Faces binds through page definitions, not over HTTP. Migrating the");
            out.println("      model alone strands the UI: the front end needs rewriting.");
        }
        if (!r.hasExistingRestContract() && !r.hasAdfFacesConsumers()) {
            out.println("    ? No REST contract and no ADF Faces consumers found.");
            out.println("      Check for SOAP/SDO service interfaces or an external consumer.");
        }
    }

    private void printByLayer(PrintStream out, ScanResult r) {
        Map<AdfArtifactType.Layer, List<Map.Entry<AdfArtifactType, Integer>>> byLayer =
                new EnumMap<>(AdfArtifactType.Layer.class);
        r.countsByType().forEach((type, count) -> {
            if (!showIgnored && type.relevance() == AdfArtifactType.Relevance.IGNORED) return;
            byLayer.computeIfAbsent(type.layer(), k -> new ArrayList<>()).add(Map.entry(type, count));
        });

        out.println();
        out.println("  ARTIFACTS");
        byLayer.forEach((layer, entries) -> {
            out.printf("    %s%n", layer);
            entries.forEach(e ->
                    out.printf("      %-46s %6d%n", e.getKey().label(), e.getValue()));
        });
    }

    private void printRelevance(PrintStream out, ScanResult r) {
        out.println();
        out.println("  BY MIGRATION RELEVANCE");
        r.countsByRelevance().forEach((relevance, count) ->
                out.printf("      %-46s %6d%n", relevance, count));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : "..." + s.substring(s.length() - (max - 3));
    }
}
