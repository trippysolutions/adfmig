package com.adfmig.core.model;

import java.util.List;
import java.util.Optional;

/**
 * An ADF application module: the transactional service facade over a set of view object instances.
 *
 * <p>Migration target: a {@code @Service} with {@code @Transactional} methods.
 *
 * <p>The semantic gap to watch is statefulness. An application module holds a transaction with
 * pending changes across multiple client calls, backed by module pooling and passivation. REST is
 * request-scoped. Modules consumed over ADF BC REST are already request-scoped and so port
 * directly; modules driven from an ADF Faces UI usually are not.
 *
 * @param viewUsages named view object instances this module exposes
 * @param nested     nested application modules, by fully qualified name
 */
public record ApplicationModule(
        String fqn,
        String componentClass,
        List<ViewUsage> viewUsages,
        List<String> nested,
        List<ViewLinkUsage> viewLinkUsages,
        String sourcePath) {

    public String simpleName() {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    public boolean hasCustomImplementation() {
        return componentClass != null && !componentClass.isBlank();
    }

    public Optional<ViewUsage> viewUsage(String name) {
        return viewUsages.stream().filter(v -> v.name().equals(name)).findFirst();
    }

    /**
     * A named instance of a view object within the module. REST resources bind to these instance
     * names, not to view object classes, so resolving the name is what connects an endpoint to
     * its query.
     *
     * @param viewObject fully qualified name of the view object
     */
    public record ViewUsage(String name, String viewObject) {}

    /**
     * A master-detail relationship wired between two view instances in this module.
     *
     * <p>This is what ADF exposes as a nested collection, so it is part of the published contract
     * even though it appears nowhere in the resource definitions.
     *
     * @param source the master view instance name
     * @param target the detail view instance name
     */
    public record ViewLinkUsage(String name, String viewLink, String source, String target) {}
}
