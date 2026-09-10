package com.adfmig.cli;

import com.adfmig.core.AdfArtifactType;
import com.adfmig.core.model.AdfApplication;
import com.adfmig.core.model.Endpoint;
import com.adfmig.core.model.EntityObject;
import com.adfmig.core.model.RestResource;
import com.adfmig.core.model.SecurityPolicy;
import com.adfmig.core.model.Types;
import com.adfmig.core.model.ViewObject;
import com.adfmig.parser.AdfApplicationParser;
import com.adfmig.parser.ApplicationDiscovery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Resolves an ADF application's published REST surface down to the database, and reports what
 * migrating it would involve.
 */
@Command(
        name = "analyze",
        mixinStandardHelpOptions = true,
        description = "Resolve the REST surface of an ADF application: URL to query to table, with security.")
public final class AnalyzeCommand implements Callable<Integer> {

    @Parameters(paramLabel = "PATH", arity = "1..*", description = "Root directory of an ADF application.")
    private List<Path> roots;

    @Option(names = {"-o", "--json"}, description = "Write the resolved model to this file.")
    private Path jsonOut;

    @Option(names = "--sql", description = "Show the full SQL behind each endpoint.")
    private boolean showSql;

    @Option(names = "--all",
            description = "Analyse every application under the path, each on its own. "
                    + "Use this when the path holds more than one application.")
    private boolean all;


    @Override
    public Integer call() throws Exception {

        PrintStream out = System.out;
        AdfApplicationParser parser = new AdfApplicationParser();

        for (Path path : roots) {
            if (!Files.isDirectory(path)) {
                System.err.println("not a directory: " + path);
                return 2;
            }
        }

        for (Path root : expand(roots, out)) {
            AdfApplication app;
            try (var progress = Terminal.Progress.on(out, "Reading " + root.getFileName())) {
                app = parser.parse(root, progress::update);
                progress.done(app.entities().size() + " entities, "
                        + app.viewObjects().size() + " view objects");
            }
            report(out, app);

            if (jsonOut != null) {
                ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                if (jsonOut.getParent() != null) Files.createDirectories(jsonOut.getParent());
                mapper.writeValue(jsonOut.toFile(), app.endpoints());
                out.printf("%nWrote %s%n", jsonOut.toAbsolutePath());
            }
        }
        return 0;
    }

    /**
     * Expands each given path into the application roots to analyse.
     *
     * <p>Without {@code --all} the paths are used as given, and a path holding several
     * applications is refused rather than merged. With it, every application found is analysed
     * separately — never merged, since merging collides unrelated components under shared names.
     */
    private List<Path> expand(List<Path> paths, PrintStream out) throws Exception {
        if (!all) return paths;

        ApplicationDiscovery discovery = new ApplicationDiscovery();
        List<Path> expanded = new java.util.ArrayList<>();
        for (Path path : paths) {
            List<ApplicationDiscovery.Found> found = discovery.discover(path);
            found.forEach(f -> expanded.add(f.root()));
            out.printf("%nAnalysing %d application(s) under %s%n", found.size(), path);
        }
        return expanded;
    }

    private void report(PrintStream out, AdfApplication app) {
        out.println();
        out.println("=".repeat(96));
        out.println(Path.of(app.root()).getFileName());
        out.println("=".repeat(96));

        if (!multipleApplications(out, app)) return;

        out.println();
        out.println("  MODEL");
        out.printf("      %-26s %5d%n", "Entity objects", app.entities().size());
        out.printf("      %-26s %5d%n", "View objects", app.viewObjects().size());
        out.printf("      %-26s %5d%n", "Application modules", app.modules().size());
        out.printf("      %-26s %5d%n", "REST resources", app.restResources().size());
        out.printf("      %-26s %5d%n", "Security policies", app.policies().size());

        List<Endpoint> endpoints = app.endpoints();
        if (endpoints.isEmpty()) {
            out.println();
            out.println("  No published REST surface found.");
        } else {
            out.printf("%n  REST SURFACE (%d endpoints)%n", endpoints.size());
            endpoints.forEach(e -> endpoint(out, e));
        }

        security(out, app, endpoints);
        migrationNotes(out, app, endpoints);
    }

    /**
     * Refuses to analyse a path holding more than one application.
     *
     * <p>Repositories and shared drives commonly hold many applications side by side — the public
     * {@code a large sample of real applications} repository holds 198. Merging them produces a model where
     * components from unrelated applications collide on the same fully qualified name and
     * references resolve across application boundaries, so every number reported is wrong. Saying
     * so is far more useful than quietly reporting nonsense.
     *
     * @return true when analysis should continue
     */
    private boolean multipleApplications(PrintStream out, AdfApplication app) {
        int workspaces = app.scan().count(AdfArtifactType.JDEV_WORKSPACE);
        if (workspaces <= 1) return true;

        out.println();
        out.printf("  This path holds %d JDeveloper workspaces, not one application.%n", workspaces);
        out.println("  Analysing them together would merge unrelated components under colliding");
        out.println("  names and report meaningless totals. Point analyze at a single application");
        out.println("  root — the directory containing its .jws file. For example:");
        out.println();
        List<String> roots = app.scan().ofType(AdfArtifactType.JDEV_WORKSPACE).stream()
                .map(w -> {
                    int slash = w.path().lastIndexOf('/');
                    return slash < 0 ? app.root() : app.root() + "/" + w.path().substring(0, slash);
                })
                .distinct()
                .toList();
        roots.stream().limit(3).forEach(r -> out.printf("      adfmig analyze %s%n", r));
        if (roots.size() > 3) out.printf("      ... and %d more%n", roots.size() - 3);
        return false;
    }

    private void endpoint(PrintStream out, Endpoint e) {
        out.println();
        out.printf("  %-58s %s%n", e.url(), e.isUngranted() ? "[NO GRANT]" : "[secured]");

        if (e.collection() != null) {
            out.printf("      %-13s %s%n", "collection", e.collection());
        }

        ViewObject vo = e.viewObject();
        if (vo != null) {
            String instance = e.module() == null ? "" : "  (" + e.module().simpleName() + "." + e.resource().viewUsage() + ")";
            out.printf("      %-13s %s%s%n", "view object", vo.fqn(), instance);
        }
        if (e.entityObject() != null) {
            out.printf("      %-13s %s  ->  table %s%n", "entity",
                    e.entityObject().fqn(), e.entityObject().dbObjectName());
        } else if (vo != null && vo.isReadOnly()) {
            // Not a gap in the model: an expert-mode view has no entity behind it, and migrates
            // to a native query rather than a JPA entity.
            out.printf("      %-13s read-only%s, no entity  ->  native query%n", "entity",
                    vo.customQuery() ? " (hand-written SQL)" : "");
        }
        if (vo != null) {
            out.printf("      %-13s %d projected%n", "attributes", vo.attributes().size());
            if (showSql && vo.sql() != null) {
                out.printf("      %-13s %s%n", "sql", vo.sql());
            }
            if (!vo.variables().isEmpty()) {
                out.printf("      %-13s %s%n", "bind vars", vo.variables().stream()
                        .map(v -> v.name() + ": " + Types.simple(v.javaType()))
                        .reduce((a, b) -> a + ", " + b).orElse(""));
            }
            vo.criteria().forEach(c -> {
                String expr = c.items().stream()
                        .map(i -> i.attribute() + " " + i.operator() + " " + i.value())
                        .reduce((a, b) -> a + " " + c.conjunction() + " " + b)
                        .orElse("(empty)");
                out.printf("      %-13s %s: %s%n", "criteria", c.name(), expr);
            });
        }

        List<RestResource.Operation> operations = e.operations();
        if (!operations.isEmpty()) {
            out.printf("      %-13s %s%n", "operations", operations.get(0).signature());
            operations.stream().skip(1).forEach(op ->
                    out.printf("      %-13s %s%n", "", op.signature()));
        }

        if (!e.grantedActions().isEmpty()) {
            out.printf("      %-13s %s  ->  %s%n", "grants",
                    String.join(", ", e.grantedActions()), String.join(", ", e.principals()));
        }

        if (vo != null && vo.hasCustomImplementation()) {
            out.printf("      %-13s %s%n", "custom java", vo.componentClass());
        }
        if (!e.unresolved().isEmpty()) {
            out.printf("      %-13s %s%n", "UNRESOLVED", String.join("; ", e.unresolved()));
        }
    }

    private void security(PrintStream out, AdfApplication app, List<Endpoint> endpoints) {
        out.println();
        out.println("  SECURITY");

        List<Endpoint> ungranted = endpoints.stream().filter(Endpoint::isUngranted).toList();
        if (!ungranted.isEmpty()) {
            out.printf("      %d endpoint(s) carry no grant. In ADF these are unreachable, so the%n",
                    ungranted.size());
            out.println("      generated application must deny them rather than publish them open:");
            ungranted.forEach(e -> out.printf("        %s%n", e.url()));
        }

        Set<String> userGrants = new LinkedHashSet<>();
        boolean fileIdentityStore = false;
        for (SecurityPolicy policy : app.policies()) {
            policy.grants().stream().filter(SecurityPolicy.Grant::isUserGrant)
                    .forEach(g -> userGrants.add(g.principalName()));
            if (!policy.users().isEmpty()) fileIdentityStore = true;
        }
        if (!userGrants.isEmpty()) {
            out.printf("      Permissions granted to named users rather than roles: %s%n",
                    String.join(", ", userGrants));
            out.println("      Roles must be introduced before these become Spring Security authorities.");
        }
        if (fileIdentityStore) {
            out.println("      Users are defined in the policy file itself, so the application");
            out.println("      authenticates against a file rather than a directory. The migrated");
            out.println("      application needs a real identity provider.");
        }
        if (ungranted.isEmpty() && userGrants.isEmpty() && !fileIdentityStore) {
            out.println("      No issues found in the declared policy.");
        }
    }

    private void migrationNotes(PrintStream out, AdfApplication app, List<Endpoint> endpoints) {
        out.println();
        out.println("  MIGRATION NOTES");

        long unresolved = endpoints.stream().filter(e -> !e.isFullyResolved()).count();
        if (unresolved > 0) {
            out.printf("      %d endpoint(s) could not be resolved to a table.%n", unresolved);
        }

        List<String> custom = app.customImplementations();
        if (!custom.isEmpty()) {
            out.printf("      %d component(s) carry hand-written Java that metadata cannot describe:%n",
                    custom.size());
            custom.forEach(c -> out.printf("        %s%n", c));
        }

        Set<String> domainTypes = new LinkedHashSet<>();
        app.entities().values().forEach(e -> e.attributes().stream()
                .map(EntityObject.Attribute::javaType)
                .filter(Types::isAdfDomainType)
                .forEach(domainTypes::add));
        if (!domainTypes.isEmpty()) {
            out.println("      ADF runtime types in use; these must be mapped, not carried across:");
            domainTypes.forEach(t -> out.printf("        %-38s -> %s%n", t, Types.toJava(t)));
        }

        long expertMode = app.viewObjects().values().stream().filter(ViewObject::customQuery).count();
        if (expertMode > 0) {
            out.printf("      %d view object(s) carry hand-written SQL, which must be carried across%n",
                    expertMode);
            out.println("      as native queries rather than regenerated from the entity model.");
        }

        List<EntityObject> deadEntities = app.unreferencedEntities();
        List<ViewObject> deadViews = app.unreferencedViewObjects();
        if (!deadEntities.isEmpty() || !deadViews.isEmpty()) {
            out.printf("      Unreferenced: %d entity object(s), %d view object(s) — likely dead code.%n",
                    deadEntities.size(), deadViews.size());
        }

        long expressionRules = app.entities().values().stream()
                .flatMap(e -> e.validators().stream())
                .filter(EntityObject.Validator::expression)
                .count();
        if (expressionRules > 0) {
            out.printf("      %d validation rule(s) delegate to Groovy expressions (.bcs), which need%n",
                    expressionRules);
            out.println("      translation rather than a direct annotation.");
        }
    }
}
