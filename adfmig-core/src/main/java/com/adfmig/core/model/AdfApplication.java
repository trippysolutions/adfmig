package com.adfmig.core.model;

import com.adfmig.core.ScanResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A parsed ADF application: every business component, REST resource and security policy found,
 * indexed by fully qualified name so the links between them can be resolved.
 *
 * <p>This is the intermediate representation everything downstream works from. Analysis reads it,
 * generation writes from it, and the report is rendered from it. Nothing after this point looks
 * at ADF XML again.
 */
public record AdfApplication(
        String root,
        ScanResult scan,
        Map<String, EntityObject> entities,
        Map<String, ViewObject> viewObjects,
        Map<String, Association> associations,
        Map<String, ViewLink> viewLinks,
        Map<String, ApplicationModule> modules,
        Map<String, List<GroovyExpression>> groovyExpressions,
        Map<String, CustomJavaClass> customJava,
        Map<String, RestResource> restResources,
        List<ScreenBinding> screens,
        List<RestRegistry> registries,
        List<SecurityPolicy> policies) {

    /**
     * Resolves every published REST path to its backing query, table and security grants.
     *
     * <p>The chain is: registry path to resource definition, resource definition to application
     * module view usage, view usage to view object, view object to entity object, and separately
     * resource name to security grants.
     */
    public List<Endpoint> endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (RestRegistry registry : registries) {
            for (RestRegistry.PathEntry entry : registry.paths()) {
                endpoints.add(resolve(registry, entry));
            }
        }
        endpoints.sort((a, b) -> a.url().compareTo(b.url()));
        return endpoints;
    }

    private Endpoint resolve(RestRegistry registry, RestRegistry.PathEntry entry) {
        List<String> unresolved = new ArrayList<>();

        RestResource resource = registry.resolveUsage(entry.usageId())
                .map(restResources::get)
                .orElse(null);
        if (resource == null) unresolved.add("resource definition for usage '" + entry.usageId() + "'");

        ApplicationModule module = resource == null ? null : findModule(registry, resource);
        if (resource != null && module == null) {
            unresolved.add("application module for data control '" + resource.dataControl() + "'");
        }

        ViewObject viewObject = null;
        if (module != null && resource != null) {
            Optional<ApplicationModule.ViewUsage> usage = module.viewUsage(resource.viewUsage());
            if (usage.isEmpty()) {
                unresolved.add("view usage '" + resource.viewUsage() + "' in " + module.simpleName());
            } else {
                viewObject = viewObjects.get(usage.get().viewObject());
                if (viewObject == null) unresolved.add("view object " + usage.get().viewObject());
            }
        }

        EntityObject entity = null;
        if (viewObject != null && !viewObject.entityUsages().isEmpty()) {
            String entityFqn = viewObject.entityUsages().get(0).entity();
            entity = entities.get(entityFqn);
            if (entity == null) unresolved.add("entity object " + entityFqn);
        }

        Set<String> actions = new LinkedHashSet<>();
        Set<String> principals = new LinkedHashSet<>();
        for (SecurityPolicy policy : policies) {
            actions.addAll(policy.actionsOn(entry.resourceName()));
            principals.addAll(policy.principalsOn(entry.resourceName()));
        }

        return new Endpoint(entry.url(), entry.resourceName(), entry.version(),
                resource, module, viewObject, entity, actions, principals, unresolved);
    }

    /**
     * Finds the application module behind a resource. Prefers the name derived from the data
     * control, then falls back to any module that declares the view usage the resource binds to —
     * naming conventions do get broken in long-lived applications.
     */
    private ApplicationModule findModule(RestRegistry registry, RestResource resource) {
        ApplicationModule byName = registry.dataControl(resource.dataControl())
                .map(RestRegistry.DataControl::probableModuleFqn)
                .map(modules::get)
                .orElse(null);
        if (byName != null) return byName;

        return modules.values().stream()
                .filter(m -> m.viewUsage(resource.viewUsage()).isPresent())
                .findFirst()
                .orElse(null);
    }

    /**
     * The hand-written class behind a component, when one was found and could be read.
     *
     * <p>A component can declare a custom class the scan never sees — the source may live in
     * another project, or only inside a library. An empty result means "not described", not
     * "not there".
     */
    public java.util.Optional<CustomJavaClass> customJavaFor(String className) {
        return className == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(customJava.get(className));
    }

    /** The Groovy expressions belonging to one component, or an empty list when it has none. */
    public List<GroovyExpression> groovyFor(String componentFqn) {
        return groovyExpressions.getOrDefault(componentFqn, List.of());
    }

    /**
     * Entity accessors whose association is missing from the model, keyed by entity. Each one is a
     * relationship that cannot be generated, because the join columns and target entity live in
     * the association rather than on the accessor.
     */
    public List<String> unresolvedAssociations() {
        List<String> unresolved = new ArrayList<>();
        entities.values().forEach(entity -> entity.accessors().forEach(accessor -> {
            Association association = associations.get(accessor.association());
            if (association == null) {
                unresolved.add(entity.simpleName() + "." + accessor.name()
                        + " -> " + accessor.association());
            } else if (!association.isMappable()) {
                unresolved.add(entity.simpleName() + "." + accessor.name()
                        + " -> " + association.simpleName() + " (incomplete)");
            }
        }));
        return unresolved;
    }

    /**
     * The nested collections ADF published beneath a parent row.
     *
     * <p>Resolved from the application module's view link usages: each names a master and a detail
     * view instance, and the view link behind it carries the columns they join on. Only children
     * hanging off a view instance that is itself published get an endpoint — a child of a
     * collection nobody can reach was not reachable in ADF either.
     */
    public List<MasterDetail> masterDetails() {
        List<MasterDetail> found = new ArrayList<>();
        List<Endpoint> published = endpoints();

        // A URL derived from a screen is a parent too. ADF wired the relationship between two
        // view instances; whether the parent happens to be a published resource or a URL this
        // tool derived changes nothing about the relationship, and insisting on the first left
        // every relationship in an application that publishes no REST with nowhere to live.
        List<ScreenEndpoint> screens = screenEndpoints();

        for (ApplicationModule module : modules.values()) {
            for (ApplicationModule.ViewLinkUsage usage : module.viewLinkUsages()) {
                Endpoint master = published.stream()
                        .filter(e -> e.resource() != null
                                && usage.source().equals(e.resource().viewUsage()))
                        .findFirst()
                        .orElse(null);
                String masterUrl = master != null ? master.url() : screens.stream()
                        .filter(screen -> usage.source().equals(screen.viewUsage()))
                        .map(ScreenEndpoint::url)
                        .findFirst()
                        .orElse(null);
                if (masterUrl == null) continue;

                ViewObject detailView = module.viewUsage(usage.target())
                        .map(ApplicationModule.ViewUsage::viewObject)
                        .map(viewObjects::get)
                        .orElse(null);
                if (detailView == null) continue;

                ViewLink link = viewLinks.get(usage.viewLink());
                found.add(new MasterDetail(
                        master,
                        masterUrl,
                        usage.source(),
                        usage.target(),
                        detailView,
                        detailView.entityUsages().isEmpty() ? null
                                : entities.get(detailView.entityUsages().get(0).entity()),
                        link == null ? List.of() : link.master().map(ViewLink.End::attributes).orElse(List.of()),
                        link == null ? List.of() : link.detail().map(ViewLink.End::attributes).orElse(List.of()),
                        module.sourcePath()));
            }
        }
        return found;
    }

    /**
     * The endpoints an ADF Faces application needs, derived from what its screens read.
     *
     * <p>Such an application publishes no REST, so there is no contract to preserve. There is,
     * however, a precise record of what each screen required, and an API built from it serves the
     * same information the application already showed — which is what a rebuilt front end needs.
     *
     * <p>One endpoint per view instance the screens read, not one per screen: two screens showing
     * the same collection want the same endpoint, and a URL per screen would bake today's page
     * layout into tomorrow's API.
     *
     * <p>Empty when the application already publishes REST, since then the contract is the
     * authority and inventing URLs alongside it would confuse the two.
     */
    public List<ScreenEndpoint> screenEndpoints() {
        if (!restResources.isEmpty()) return List.of();

        Map<String, ScreenEndpoint> byViewUsage = new LinkedHashMap<>();
        for (ScreenBinding screen : screens) {
            for (ScreenBinding.Collection collection : screen.collections()) {
                if (collection.viewUsage() == null) continue;

                ApplicationModule module = modules.values().stream()
                        .filter(m -> m.viewUsage(collection.viewUsage()).isPresent())
                        .findFirst()
                        .orElse(null);
                if (module == null) continue;

                ViewObject view = module.viewUsage(collection.viewUsage())
                        .map(ApplicationModule.ViewUsage::viewObject)
                        .map(viewObjects::get)
                        .orElse(null);
                if (view == null) continue;

                ScreenEndpoint existing = byViewUsage.get(collection.viewUsage());
                if (existing == null) {
                    byViewUsage.put(collection.viewUsage(), new ScreenEndpoint(
                            collection.viewUsage(), module, view,
                            view.entityUsages().isEmpty() ? null
                                    : entities.get(view.entityUsages().get(0).entity()),
                            collection.rangeSize(),
                            new ArrayList<>(List.of(screen.id()))));
                } else if (!existing.usedByScreens().contains(screen.id())) {
                    existing.usedByScreens().add(screen.id());
                }
            }
        }
        return List.copyOf(byViewUsage.values());
    }

    /** Entity objects no view object reads. Usually dead code, and a straight cost saving. */
    public List<EntityObject> unreferencedEntities() {
        Set<String> used = new LinkedHashSet<>();
        viewObjects.values().forEach(vo ->
                vo.entityUsages().forEach(u -> used.add(u.entity())));
        return entities.values().stream()
                .filter(e -> !used.contains(e.fqn()))
                .sorted((a, b) -> a.fqn().compareTo(b.fqn()))
                .toList();
    }

    /** View objects no application module exposes. Usually dead code. */
    public List<ViewObject> unreferencedViewObjects() {
        Set<String> used = new LinkedHashSet<>();
        modules.values().forEach(m ->
                m.viewUsages().forEach(u -> used.add(u.viewObject())));
        return viewObjects.values().stream()
                .filter(v -> !used.contains(v.fqn()))
                .sorted((a, b) -> a.fqn().compareTo(b.fqn()))
                .toList();
    }

    /** Components carrying hand-written Java, which no generator can reproduce from metadata. */
    public List<String> customImplementations() {
        List<String> out = new ArrayList<>();
        entities.values().stream().filter(EntityObject::hasCustomImplementation)
                .forEach(e -> out.add(e.fqn() + " -> " + e.rowClass()));
        viewObjects.values().stream().filter(ViewObject::hasCustomImplementation)
                .forEach(v -> out.add(v.fqn() + " -> " + v.componentClass()));
        modules.values().stream().filter(ApplicationModule::hasCustomImplementation)
                .forEach(m -> out.add(m.fqn() + " -> " + m.componentClass()));
        out.sort(String::compareTo);
        return out;
    }
}
