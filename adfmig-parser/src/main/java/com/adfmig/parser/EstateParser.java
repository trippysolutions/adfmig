package com.adfmig.parser;

import com.adfmig.core.AdfArtifact;
import com.adfmig.core.AdfArtifactType;
import com.adfmig.core.Progress;
import com.adfmig.core.ScanResult;
import com.adfmig.core.estate.AdfEstate;
import com.adfmig.core.estate.CredentialFinding;
import com.adfmig.core.estate.DatabaseConnection;
import com.adfmig.core.estate.DiscoveredApplication;
import com.adfmig.core.estate.ProjectDependency;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Surveys a directory tree holding one or more ADF applications: what applications are present,
 * how each is consumed, which database each reaches, and which depend on one another.
 *
 * <p>This runs before any deep parsing. It answers the questions that decide the shape and the
 * price of a migration — how many applications, how many are already REST, which share a schema,
 * and in what order they can be moved.
 */
public final class EstateParser {

    private final ApplicationDiscovery discovery = new ApplicationDiscovery();
    private final ProjectScanner scanner = new ProjectScanner();
    private final Bc4jConfigParser connections = new Bc4jConfigParser();
    private final ProjectDependencyParser projectDependencies = new ProjectDependencyParser();
    private final CredentialScanner credentials = new CredentialScanner();

    public AdfEstate parse(Path estateRoot) throws IOException {
        return parse(estateRoot, Progress.SILENT);
    }

    /** @param progress told which application is being read, for callers that show it */
    public AdfEstate parse(Path estateRoot, Progress progress) throws IOException {
        Path root = estateRoot.toAbsolutePath().normalize();
        List<ApplicationDiscovery.Found> found = discovery.discover(root);

        List<DiscoveredApplication> applications = new ArrayList<>();
        List<CredentialFinding> credentialFindings = new ArrayList<>();
        Map<String, List<DatabaseConnection>> connectionsByApplication = new LinkedHashMap<>();
        // Sorted longest-first so a project inside a nested application is attributed to the
        // innermost application containing it, not to an enclosing one.
        TreeMap<String, String> applicationByPathPrefix =
                new TreeMap<>((a, b) -> b.length() != a.length() ? b.length() - a.length() : a.compareTo(b));

        int index = 0;
        for (ApplicationDiscovery.Found application : found) {
            progress.report(String.format("%d of %d  %s", ++index, found.size(), application.name()));
            ScanResult scan = scanner.scan(application.root());
            String path = relativize(root, application.root());

            applications.add(new DiscoveredApplication(
                    application.name(),
                    path,
                    application.workspaceFile() == null ? null : relativize(root, application.workspaceFile()),
                    scan.count(AdfArtifactType.ENTITY_OBJECT),
                    scan.count(AdfArtifactType.VIEW_OBJECT),
                    scan.count(AdfArtifactType.APPLICATION_MODULE),
                    scan.count(AdfArtifactType.REST_RESOURCE),
                    scan.count(AdfArtifactType.PAGE_DEFINITION),
                    scan.count(AdfArtifactType.JSF_PAGE) + scan.count(AdfArtifactType.JSF_FRAGMENT),
                    scan.count(AdfArtifactType.JAVA_SOURCE)));

            applicationByPathPrefix.put(application.root().toString(), path);
            connectionsByApplication.put(path, databaseConnections(application.root(), scan));

            for (CredentialFinding finding : credentials.scan(application.root())) {
                credentialFindings.add(new CredentialFinding(
                        prefix(path, finding.path()), finding.kind(),
                        finding.location(), finding.occurrences()));
            }
        }

        progress.report("resolving dependencies between applications");
        return new AdfEstate(root.toString(), applications,
                dependencies(root, found, applicationByPathPrefix), connectionsByApplication,
                credentialFindings);
    }

    private List<DatabaseConnection> databaseConnections(Path applicationRoot, ScanResult scan) {
        List<DatabaseConnection> found = new ArrayList<>();
        for (AdfArtifact artifact : scan.ofType(AdfArtifactType.BC4J_CONFIG)) {
            found.addAll(connections.parse(applicationRoot.resolve(artifact.path()), artifact.path()));
        }
        return found;
    }

    /**
     * Reads every project file in the estate and records which application each dependency runs
     * between. A dependency whose endpoints sit in different applications is an import of shared
     * business components.
     */
    private List<ProjectDependency> dependencies(
            Path root,
            List<ApplicationDiscovery.Found> applications,
            TreeMap<String, String> applicationByPathPrefix) throws IOException {

        List<ProjectDependency> out = new ArrayList<>();
        for (ApplicationDiscovery.Found application : applications) {
            ScanResult scan = scanner.scan(application.root());
            for (AdfArtifact artifact : scan.ofType(AdfArtifactType.JDEV_PROJECT)) {
                Path projectFile = application.root().resolve(artifact.path());
                for (ProjectDependencyParser.Dependency dependency : projectDependencies.parse(projectFile)) {
                    out.add(new ProjectDependency(
                            relativize(root, dependency.fromProject()),
                            relativize(root, dependency.toProject()),
                            owningApplication(dependency.fromProject(), applicationByPathPrefix),
                            owningApplication(dependency.toProject(), applicationByPathPrefix)));
                }
            }
        }
        return out;
    }

    /** The application a path sits inside, or {@code null} when it points outside the estate. */
    private static String owningApplication(Path path, TreeMap<String, String> applicationByPathPrefix) {
        if (path == null) return null;
        String candidate = path.toString();
        for (Map.Entry<String, String> entry : applicationByPathPrefix.entrySet()) {
            if (candidate.equals(entry.getKey()) || candidate.startsWith(entry.getKey() + "/")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String prefix(String applicationPath, String pathInApplication) {
        return ".".equals(applicationPath) ? pathInApplication : applicationPath + "/" + pathInApplication;
    }

    private static String relativize(Path root, Path path) {
        if (path == null) return null;
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(root)) return absolute.toString();
        String relative = root.relativize(absolute).toString().replace('\\', '/');
        return relative.isEmpty() ? "." : relative;
    }
}
