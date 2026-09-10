package com.adfmig.core.estate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A set of ADF applications sharing a directory tree, with the dependencies between them.
 *
 * <p>Real customers do not present one application. They present a repository or a shared drive
 * holding dozens, some of which import business components from others and many of which write
 * the same database schema. Migration planning happens at this level, not at the level of a single
 * application.
 *
 * @param connectionsByApplication database connections declared by each application, keyed by the
 *                                 application's path
 */
public record AdfEstate(
        String root,
        List<DiscoveredApplication> applications,
        List<ProjectDependency> dependencies,
        Map<String, List<DatabaseConnection>> connectionsByApplication,
        List<CredentialFinding> credentialFindings) {

    /** Applications relying on optimistic locking, which generated entities must keep enforcing. */
    public List<String> applicationsUsingOptimisticLocking() {
        return connectionsByApplication.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(DatabaseConnection::isOptimisticLocking))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Dependencies that cross an application boundary — the ones that constrain migration order. */
    public List<ProjectDependency> crossApplicationDependencies() {
        return dependencies.stream().filter(ProjectDependency::isCrossApplication).toList();
    }

    /**
     * The order applications should be migrated in, dependencies first.
     *
     * <p>An application that publishes shared business components must be migrated before the
     * applications importing them, so the shared module exists for them to depend on.
     *
     * <p>Cycles are not an error to reject: ADF estates do contain mutually dependent projects.
     * Any application left in a cycle is appended after the resolvable ones, and reported by
     * {@link #dependencyCycles()} so a human can break the cycle deliberately.
     */
    public List<DiscoveredApplication> migrationOrder() {
        Map<String, Set<String>> dependsOn = new LinkedHashMap<>();
        applications.forEach(a -> dependsOn.put(a.path(), new LinkedHashSet<>()));

        for (ProjectDependency d : crossApplicationDependencies()) {
            Set<String> targets = dependsOn.get(d.fromApplication());
            if (targets != null && dependsOn.containsKey(d.toApplication())) {
                targets.add(d.toApplication());
            }
        }

        List<DiscoveredApplication> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        Map<String, DiscoveredApplication> byPath = new LinkedHashMap<>();
        applications.forEach(a -> byPath.put(a.path(), a));

        boolean progress = true;
        while (progress && placed.size() < applications.size()) {
            progress = false;
            for (DiscoveredApplication app : applications) {
                if (placed.contains(app.path())) continue;
                if (placed.containsAll(dependsOn.get(app.path()))) {
                    ordered.add(app);
                    placed.add(app.path());
                    progress = true;
                }
            }
        }

        // Whatever remains sits in a cycle. Include it rather than silently dropping it.
        applications.stream().filter(a -> !placed.contains(a.path())).forEach(ordered::add);
        return ordered;
    }

    /** Applications that could not be ordered because they depend on one another. */
    public List<DiscoveredApplication> dependencyCycles() {
        List<DiscoveredApplication> ordered = migrationOrder();
        Set<String> resolvable = new HashSet<>();
        Map<String, Set<String>> dependsOn = new HashMap<>();
        applications.forEach(a -> dependsOn.put(a.path(), new LinkedHashSet<>()));
        for (ProjectDependency d : crossApplicationDependencies()) {
            Set<String> targets = dependsOn.get(d.fromApplication());
            if (targets != null && dependsOn.containsKey(d.toApplication())) targets.add(d.toApplication());
        }
        boolean progress = true;
        while (progress) {
            progress = false;
            for (DiscoveredApplication app : ordered) {
                if (resolvable.contains(app.path())) continue;
                if (resolvable.containsAll(dependsOn.get(app.path()))) {
                    resolvable.add(app.path());
                    progress = true;
                }
            }
        }
        return applications.stream().filter(a -> !resolvable.contains(a.path())).toList();
    }

    /**
     * Applications writing the same database schema.
     *
     * <p>This is the finding most likely to be missed and most expensive to miss. Migrating one of
     * these applications to JPA while its neighbour stays on ADF leaves two writers against the
     * same rows with different optimistic-locking mechanisms — ADF's change indicator on one side
     * and Hibernate's version column on the other. Neither notices the other's writes, so updates
     * are silently lost in production.
     *
     * <p>Applications sharing a schema must therefore be migrated together, or a locking strategy
     * agreed across both before either moves.
     *
     * @return schema identity mapped to the applications reaching it, only where more than one does
     */
    public Map<String, List<String>> schemaConflicts() {
        Map<String, Set<String>> byIdentity = new LinkedHashMap<>();
        connectionsByApplication.forEach((application, connections) ->
                connections.forEach(c -> byIdentity
                        .computeIfAbsent(c.schemaIdentity(), k -> new LinkedHashSet<>())
                        .add(application)));

        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        byIdentity.forEach((identity, apps) -> {
            if (apps.size() > 1) conflicts.put(identity, List.copyOf(apps));
        });
        return conflicts;
    }

    /** Applications grouped by how they are consumed. */
    public Map<DiscoveredApplication.Profile, List<DiscoveredApplication>> byProfile() {
        Map<DiscoveredApplication.Profile, List<DiscoveredApplication>> out = new LinkedHashMap<>();
        for (DiscoveredApplication.Profile profile : DiscoveredApplication.Profile.values()) {
            List<DiscoveredApplication> matching = applications.stream()
                    .filter(a -> a.profile() == profile)
                    .toList();
            if (!matching.isEmpty()) out.put(profile, matching);
        }
        return out;
    }
}
