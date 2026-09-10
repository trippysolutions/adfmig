package com.adfmig.core.estate;

/**
 * A dependency one JDeveloper project declares on another.
 *
 * <p>When the two projects belong to different applications, the dependency crosses an
 * application boundary — typically an ADF Library JAR, where one application packages business
 * components that another imports. Those shared components must be migrated once, into a module
 * both generated applications depend on, rather than duplicated into each.
 *
 * @param fromApplication the depending application's path, or {@code null} when unresolved
 * @param toApplication   the depended-upon application's path, or {@code null} when unresolved
 */
public record ProjectDependency(
        String fromProject,
        String toProject,
        String fromApplication,
        String toApplication) {

    public boolean isCrossApplication() {
        return fromApplication != null && toApplication != null && !fromApplication.equals(toApplication);
    }
}
