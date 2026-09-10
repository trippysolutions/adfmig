package com.adfmig.core.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The authorization policy declared in {@code jazn-data.xml}.
 *
 * <p>For ADF BC REST applications this maps onto Spring Security with no inference required:
 * grants of {@code RestServicePermission} name the resource and the HTTP actions allowed on it,
 * so an endpoint-level rule follows directly. That is not true of ADF Faces applications, where
 * permissions are attached to task flows and pages and the endpoint mapping has to be recovered
 * from the call graph.
 *
 * @param applicationName the policy's application name
 * @param grants          every permission grant found
 * @param users           users defined in the file's own identity store, if any. Their presence
 *                        means the application authenticates against a file rather than a
 *                        directory, which will not survive the migration.
 */
public record SecurityPolicy(
        String applicationName,
        List<Grant> grants,
        List<String> users,
        String sourcePath) {

    /** All actions granted on a named resource, across every principal. */
    public Set<String> actionsOn(String resourceName) {
        Set<String> actions = new LinkedHashSet<>();
        grants.stream()
                .filter(g -> g.resourceName().equals(resourceName))
                .forEach(g -> actions.addAll(g.actions()));
        return actions;
    }

    /** Principals granted anything on a named resource. */
    public Set<String> principalsOn(String resourceName) {
        Set<String> principals = new LinkedHashSet<>();
        grants.stream()
                .filter(g -> g.resourceName().equals(resourceName))
                .forEach(g -> principals.add(g.principalName()));
        return principals;
    }

    /**
     * One permission grant.
     *
     * @param principalClass ADF's principal implementation. {@code JpsApplicationRole} is an
     *                       application role and maps to a Spring authority;
     *                       {@code JpsXmlUserImpl} is an individual user, which is a development
     *                       shortcut that must not be carried into production.
     * @param permissionClass the ADF permission type, e.g.
     *                        {@code oracle.adf.share.security.authorization.RestServicePermission}
     * @param resourceName    the resource the permission applies to
     * @param actions         the granted actions, e.g. get, create, update, delete, describe, invoke
     */
    public record Grant(
            String principalClass,
            String principalName,
            String permissionClass,
            String resourceName,
            List<String> actions) {

        /** True when the grantee is a named user rather than a role. */
        public boolean isUserGrant() {
            return principalClass != null && principalClass.endsWith("JpsXmlUserImpl");
        }

        /** True when this grant governs a REST resource, and so maps directly to an endpoint rule. */
        public boolean isRestGrant() {
            return permissionClass != null && permissionClass.endsWith("RestServicePermission");
        }
    }
}
