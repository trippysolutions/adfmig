package com.adfmig.core.analysis;

import com.adfmig.core.AdfArtifactType;
import com.adfmig.core.model.AdfApplication;
import com.adfmig.core.model.Endpoint;
import com.adfmig.core.model.SecurityPolicy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The migration assessment for one application: what it contains, what each part costs, and what
 * the whole comes to.
 */
public record ApplicationAssessment(
        AdfApplication application,
        List<ArtifactAssessment> artifacts,
        EffortModel effort) {

    /** Artifact counts by migration class. */
    public Map<MigrationClass, Integer> countsByClass() {
        Map<MigrationClass, Integer> counts = new LinkedHashMap<>();
        for (MigrationClass migrationClass : MigrationClass.values()) counts.put(migrationClass, 0);
        artifacts.forEach(a -> counts.merge(a.migrationClass(), 1, Integer::sum));
        counts.values().removeIf(v -> v == 0);
        return counts;
    }

    /** Person-days by migration class, for the business components themselves. */
    public Map<MigrationClass, Double> daysByClass() {
        Map<MigrationClass, Double> days = new LinkedHashMap<>();
        artifacts.forEach(a -> days.merge(a.migrationClass(), a.effortDays(), Double::sum));
        return days;
    }

    public double componentDays() {
        return artifacts.stream().mapToDouble(ArtifactAssessment::effortDays).sum();
    }

    /** Roles and users named in any grant, which security configuration has to reproduce. */
    public Set<String> principals() {
        Set<String> principals = new LinkedHashSet<>();
        application.policies().forEach(p ->
                p.grants().forEach(g -> principals.add(g.principalName())));
        principals.remove(null);
        return principals;
    }

    public double securityDays() {
        if (application.policies().isEmpty()) return 0;
        return effort.securityBase() + principals().size() * effort.perRole();
    }

    /**
     * Contract tests, one per published endpoint: the same request against the ADF service and
     * its replacement, with the responses compared. This is what makes a migration provable rather
     * than merely finished, and it is why applications that already publish REST are cheap.
     */
    public double contractTestDays() {
        return application.endpoints().size() * effort.perEndpointTest();
    }

    public double setupDays() {
        return effort.applicationSetup();
    }

    /**
     * Whether there is anything here to migrate at all.
     *
     * <p>A JDeveloper workspace can hold projects that are not applications: schema and DDL
     * projects, a library of nothing but configuration, a shell that exists to be depended on.
     * They have no entity, no view object and no application module, and there is no backend in
     * them to replace.
     */
    public boolean hasBusinessModel() {
        return !artifacts.isEmpty();
    }

    /** Backend migration total, excluding any front-end rebuild. */
    public double backendDays() {
        // Scaffolding is owed only when there is something to put in the project. Charging five
        // days to stand up an empty Spring Boot application is how an estate total stops being
        // defensible — measured against a real application with no business model in it, that
        // charge was a quarter of the whole figure.
        if (!hasBusinessModel()) return 0;
        return componentDays() + securityDays() + contractTestDays() + setupDays();
    }

    /**
     * Page definitions bound to the ADF Faces UI. Quoted separately because rebuilding a front end
     * is a different project with different people, and folding it into a backend number is how
     * estimates become indefensible.
     */
    public int pageDefinitions() {
        return application.scan().count(AdfArtifactType.PAGE_DEFINITION);
    }

    public double frontEndRebuildDays() {
        return pageDefinitions() * effort.perPageDefinitionRewrite();
    }

    /** Endpoints reachable in ADF but carrying no grant, which must be denied rather than published. */
    public List<Endpoint> ungrantedEndpoints() {
        return application.endpoints().stream().filter(Endpoint::isUngranted).toList();
    }

    /** Grants made to a named user rather than a role, which need roles introducing first. */
    public List<SecurityPolicy.Grant> userGrants() {
        return application.policies().stream()
                .flatMap(p -> p.grants().stream())
                .filter(SecurityPolicy.Grant::isUserGrant)
                .toList();
    }

    /** Components no view object or application module references. Usually removable. */
    public int unreferencedComponents() {
        return application.unreferencedEntities().size() + application.unreferencedViewObjects().size();
    }

    public String name() {
        return java.nio.file.Path.of(application.root()).getFileName().toString();
    }
}
