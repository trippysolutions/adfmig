package com.adfmig.parser;

import com.adfmig.core.AdfArtifact;
import com.adfmig.core.AdfArtifactType;
import com.adfmig.core.Progress;
import com.adfmig.core.ScanResult;
import com.adfmig.core.model.AdfApplication;
import com.adfmig.core.model.ApplicationModule;
import com.adfmig.core.model.CustomJavaClass;
import com.adfmig.core.model.Association;
import com.adfmig.core.model.EntityObject;
import com.adfmig.core.model.GroovyExpression;
import com.adfmig.core.model.RestRegistry;
import com.adfmig.core.model.RestResource;
import com.adfmig.core.model.ScreenBinding;
import com.adfmig.core.model.SecurityPolicy;
import com.adfmig.core.model.ViewLink;
import com.adfmig.core.model.ViewObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the intermediate representation of an ADF application: scan it, parse every artifact
 * that carries business meaning, and index the results by fully qualified name so their
 * references to one another resolve.
 *
 * <p>Parsing is deliberately lenient. A single unreadable view object must not abort the analysis
 * of an application with four hundred of them — it is recorded and reported instead, because an
 * artifact that cannot be read is itself a migration risk the customer needs to see.
 */
public final class AdfApplicationParser {

    private final ProjectScanner scanner = new ProjectScanner();
    private final BusinessComponentParser components = new BusinessComponentParser();
    private final RestParser rest = new RestParser();
    private final SecurityParser security = new SecurityParser();
    private final GroovyScriptParser groovy = new GroovyScriptParser();
    private final CustomJavaParser customJavaParser = new CustomJavaParser();
    private final PageDefinitionParser pageDefinitions = new PageDefinitionParser();

    public AdfApplication parse(Path root) throws IOException {
        return parse(root, Progress.SILENT);
    }

    /** @param progress told what is being read, for callers that show it */
    public AdfApplication parse(Path root, Progress progress) throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        ScanResult scan = scanner.scan(absoluteRoot);

        Map<String, EntityObject> entities = new LinkedHashMap<>();
        Map<String, ViewObject> viewObjects = new LinkedHashMap<>();
        Map<String, Association> associations = new LinkedHashMap<>();
        Map<String, ViewLink> viewLinks = new LinkedHashMap<>();
        Map<String, ApplicationModule> modules = new LinkedHashMap<>();
        Map<String, List<GroovyExpression>> groovyExpressions = new LinkedHashMap<>();
        Map<String, CustomJavaClass> customJava = new LinkedHashMap<>();
        Map<String, RestResource> restResources = new LinkedHashMap<>();
        List<RestRegistry> registries = new ArrayList<>();
        List<ScreenBinding> screens = new ArrayList<>();
        List<SecurityPolicy> policies = new ArrayList<>();

        int index = 0;
        int total = scan.artifacts().size();
        for (AdfArtifact artifact : scan.artifacts()) {
            if (++index % 25 == 0 || index == total) {
                progress.report(String.format("%d of %d artifacts", index, total));
            }
            Path file = absoluteRoot.resolve(artifact.path());
            String fqn = fqnOf(artifact);

            switch (artifact.type()) {
                case ENTITY_OBJECT -> components.parseEntity(file, artifact.path(), fqn)
                        .ifPresent(e -> entities.put(e.fqn(), e));

                case VIEW_OBJECT -> components.parseViewObject(file, artifact.path(), fqn)
                        .ifPresent(v -> viewObjects.put(v.fqn(), v));

                case ASSOCIATION -> components.parseAssociation(file, artifact.path(), fqn)
                        .ifPresent(a -> associations.put(a.fqn(), a));

                case VIEW_LINK -> components.parseViewLink(file, artifact.path(), fqn)
                        .ifPresent(v -> viewLinks.put(v.fqn(), v));

                case APPLICATION_MODULE -> components.parseApplicationModule(file, artifact.path(), fqn)
                        .ifPresent(m -> modules.put(m.fqn(), m));

                case JAVA_SOURCE -> customJavaParser.parse(file, artifact.path())
                        // Only classes extending an ADF base class carry ADF semantics. Everything
                        // else in the project is ordinary Java and not this tool's concern.
                        .filter(c -> c.kind() != CustomJavaClass.Kind.UNKNOWN)
                        .ifPresent(c -> customJava.put(c.fqn(), c));

                case GROOVY_SCRIPT -> {
                    List<GroovyExpression> expressions = groovy.parse(file, artifact.path());
                    if (!expressions.isEmpty()) groovyExpressions.put(fqn, expressions);
                }

                case REST_RESOURCE_REGISTRY -> rest.parseRegistry(file, artifact.path())
                        .ifPresent(registries::add);

                case REST_RESOURCE -> rest.parseResource(file, artifact.path(), fqn)
                        .ifPresent(r -> restResources.put(r.fqn(), r));

                case PAGE_DEFINITION -> pageDefinitions.parse(file, artifact.path())
                        .filter(screen -> !screen.isEmpty())
                        .ifPresent(screens::add);

                case JAZN_DATA -> security.parse(file, artifact.path())
                        .ifPresent(policies::add);

                default -> { /* Not parsed at this stage. */ }
            }
        }

        return new AdfApplication(absoluteRoot.toString(), scan,
                entities, viewObjects, associations, viewLinks, modules, groovyExpressions, customJava,
                restResources, screens, registries, policies);
    }

    /**
     * ADF metadata does not record its own package, so a component's fully qualified name comes
     * from its position under a source root. Where that fails — a file outside any recognised
     * source root — the path itself is used, which keeps the component in the model rather than
     * dropping it, and shows up plainly as an unresolved reference downstream.
     */
    private static String fqnOf(AdfArtifact artifact) {
        String fqn = Fqn.fromPath(artifact.path());
        if (fqn != null) return fqn;
        String name = artifact.name();
        return name != null ? name : artifact.path();
    }
}
