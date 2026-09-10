package com.adfmig.core.model;

import java.util.List;
import java.util.Set;

/**
 * One published REST endpoint with its full backing chain resolved: URL, resource definition,
 * application module, view object, entity object and security grants.
 *
 * <p>This is the unit of migration. Each endpoint becomes a Spring {@code @RestController} method
 * over a repository, with an authorization rule taken from the grants recorded here.
 *
 * <p>Any field except {@link #url} and {@link #resourceName} may be null when the chain could not
 * be resolved. That is not a parser failure to hide — a resource whose view object or entity
 * cannot be found is a genuine defect in the application, and the report must say so.
 *
 * @param grantedActions actions any principal may perform, e.g. get, create, update, delete
 * @param principals     principals holding a grant on this resource
 */
public record Endpoint(
        String url,
        String resourceName,
        String version,
        RestResource resource,
        ApplicationModule module,
        ViewObject viewObject,
        EntityObject entityObject,
        Set<String> grantedActions,
        Set<String> principals,
        List<String> unresolved) {

    /** True when every link from URL to database table was resolved. */
    public boolean isFullyResolved() {
        return unresolved.isEmpty();
    }

    /**
     * True when no grant covers this resource.
     *
     * <p>A generator must deny these by default rather than publishing them open. In ADF an
     * ungranted resource is unreachable; silently generating a {@code permitAll} endpoint would
     * turn a closed door into an open one.
     */
    public boolean isUngranted() {
        return grantedActions.isEmpty();
    }

    /** The database table behind this endpoint, when resolved. */
    public String table() {
        return entityObject == null ? null : entityObject.dbObjectName();
    }

    /**
     * The collection this path serves.
     *
     * <p>Several paths can share one resource definition, each selecting a different collection
     * binding within it — {@code /v1/Employees} and {@code /v1/EmployeesJobs} are two endpoints
     * defined by the same file. The collection whose name matches the path is the right one.
     */
    public String collection() {
        if (resource == null || resource.collections().isEmpty()) return null;
        return resource.collections().stream()
                .filter(c -> c.equals(resourceName))
                .findFirst()
                .orElseGet(() -> resource.collections().get(0));
    }

    /** Custom operations callable on this endpoint, beyond the standard collection verbs. */
    public List<RestResource.Operation> operations() {
        return resource == null ? List.of() : resource.operations();
    }
}
