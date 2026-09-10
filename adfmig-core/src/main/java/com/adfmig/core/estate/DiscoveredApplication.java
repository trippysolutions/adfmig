package com.adfmig.core.estate;

/**
 * One ADF application found within a larger directory tree.
 *
 * <p>A customer estate is rarely one application. It is a shared drive or a repository holding
 * many, and they must be analysed separately: merging them collides unrelated components under
 * the same fully qualified names and makes every reported number wrong.
 *
 * @param name          the application's name, taken from its workspace file
 * @param path          path relative to the estate root
 * @param workspaceFile the {@code .jws} that defines the application, or {@code null} when the
 *                      application was identified by other means
 */
public record DiscoveredApplication(
        String name,
        String path,
        String workspaceFile,
        int entityObjects,
        int viewObjects,
        int applicationModules,
        int restResources,
        int pageDefinitions,
        int jsfPages,
        int customJavaFiles) {

    /**
     * How this application is consumed, which decides whether it can be migrated behind an
     * unchanged contract or needs its front end rewritten. This single classification drives most
     * of the cost difference between one migration and another.
     */
    public Profile profile() {
        if (restResources > 0) return Profile.REST_CONTRACT;
        if (pageDefinitions > 0 || jsfPages > 0) return Profile.ADF_FACES_UI;
        if (entityObjects > 0 || viewObjects > 0 || applicationModules > 0) return Profile.MODEL_ONLY;
        return Profile.NO_BUSINESS_MODEL;
    }

    public enum Profile {
        /**
         * Already publishes REST. URL structure, operation signatures and security grants are all
         * declared, so the migration preserves the contract and can be verified response by
         * response.
         */
        REST_CONTRACT("already REST — contract-preserving migration"),

        /**
         * Consumed by an ADF Faces UI, which binds to the model in-process rather than over HTTP.
         * Migrating the model alone strands the front end, so this is a rewrite, not a port.
         */
        ADF_FACES_UI("ADF Faces UI — front end needs rewriting"),

        /**
         * Business components with no consumer inside the application. Usually a shared library
         * that other applications import, which makes it a dependency to migrate first.
         */
        MODEL_ONLY("model only — likely a shared library"),

        /** No business components found. */
        NO_BUSINESS_MODEL("no business model");

        private final String description;

        Profile(String description) { this.description = description; }

        public String description() { return description; }
    }

    /** Business components carrying migratable meaning. */
    public int businessComponents() {
        return entityObjects + viewObjects + applicationModules;
    }
}
