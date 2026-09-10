package com.adfmig.core.model;

import java.util.List;
import java.util.Optional;

/**
 * The {@code ResourceRegistry.rpx} manifest: every REST resource the application publishes.
 *
 * <p>This is the single most valuable file in an ADF BC REST application. It is the authoritative
 * list of what is already an API, and therefore of what can be migrated behind an unchanged
 * contract.
 *
 * @param paths        published URL paths, mapped to the resource definition serving each
 * @param usages       resource definition ids, mapped to their fully qualified page definition name
 * @param dataControls the data controls resources bind through, which resolve to application modules
 */
public record RestRegistry(
        String packageName,
        List<PathEntry> paths,
        List<Usage> usages,
        List<DataControl> dataControls,
        String sourcePath) {

    /** Resolves a path entry's usage id to the fully qualified page definition name. */
    public Optional<String> resolveUsage(String usageId) {
        return usages.stream()
                .filter(u -> u.id().equals(usageId))
                .map(Usage::path)
                .findFirst();
    }

    public Optional<DataControl> dataControl(String id) {
        return dataControls.stream().filter(d -> d.id().equals(id)).findFirst();
    }

    /**
     * One published path.
     *
     * <p>Paths carry the API version as a prefix, e.g. {@code v1_Employees}. Several paths may
     * share one resource definition, so this is not a one-to-one mapping.
     *
     * @param usageId the resource definition serving this path
     */
    public record PathEntry(String path, String usageId) {

        /** The version segment, e.g. {@code v1}, or {@code null} when the path carries none. */
        public String version() {
            int i = path.indexOf('_');
            if (i <= 0) return null;
            String head = path.substring(0, i);
            return head.matches("v\\d+") ? head : null;
        }

        /**
         * The path without its version prefix, e.g. {@code Employees}.
         *
         * <p>This is also the name security grants use, which is what lets a
         * {@code RestServicePermission} be matched to an endpoint.
         */
        public String resourceName() {
            String version = version();
            return version == null ? path : path.substring(version.length() + 1);
        }

        /** The URL this resource is served at. */
        public String url() {
            String version = version();
            return version == null ? "/" + path : "/" + version + "/" + resourceName();
        }
    }

    public record Usage(String id, String path) {}

    /**
     * A data control binding resources to an application module.
     *
     * @param configuration the {@code bc4j.xcfg} configuration name, which carries the JDBC
     *                      connection the module runs against
     */
    public record DataControl(String id, String packageName, String configuration) {

        /**
         * The application module this data control fronts.
         *
         * <p>ADF names data controls after their module, suffixed {@code DataControl}, and stores
         * the module's package alongside. Callers should treat this as a hint and fall back to
         * matching on view usage names when no module is found at the derived name.
         */
        public String probableModuleFqn() {
            String base = id.endsWith("DataControl") ? id.substring(0, id.length() - "DataControl".length()) : id;
            return packageName == null || packageName.isBlank() ? base : packageName + "." + base;
        }
    }
}
