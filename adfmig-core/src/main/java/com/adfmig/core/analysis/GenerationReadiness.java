package com.adfmig.core.analysis;

import com.adfmig.core.model.AdfApplication;
import com.adfmig.core.model.Endpoint;
import com.adfmig.core.model.EntityObject;
import com.adfmig.core.model.Types;
import com.adfmig.core.model.ViewObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the parsed model actually contains enough to emit working Spring Boot code.
 *
 * <p>This is deliberately stricter than the complexity score. Complexity asks how much work a
 * migration is; readiness asks whether the generator has the facts it needs at all. A component
 * can be trivially simple and still be impossible to generate — an entity with no primary key is
 * the easiest thing in the application and JPA still cannot map it.
 *
 * <p>The distinction matters before building a generator: it separates "the tool must be cleverer"
 * from "the source does not say", and only the first is worth engineering.
 */
public record GenerationReadiness(String fqn, Target target, List<String> blockers) {

    public enum Target { JPA_ENTITY, REPOSITORY_QUERY, REST_ENDPOINT }

    public boolean isReady() {
        return blockers.isEmpty();
    }

    /** Checks every component of an application. */
    public static List<GenerationReadiness> of(AdfApplication application) {
        List<GenerationReadiness> out = new ArrayList<>();
        application.entities().values().forEach(e -> out.add(forEntity(e, application)));
        application.viewObjects().values().forEach(v -> out.add(forViewObject(v)));
        application.endpoints().forEach(e -> out.add(forEndpoint(e)));
        return out;
    }

    /** What a JPA entity needs: a table, columns, mappable types and an identity. */
    static GenerationReadiness forEntity(EntityObject entity, AdfApplication application) {
        List<String> blockers = new ArrayList<>();

        if (isBlank(entity.dbObjectName())) {
            blockers.add("no table or view mapped");
        }
        if (entity.attributes().isEmpty()) {
            blockers.add("no attributes declared");
        }
        if (entity.primaryKey().isEmpty()) {
            // Hibernate cannot map an identity without one, and inventing a key silently would be
            // worse than refusing: it changes what the application means.
            blockers.add("no primary key");
        }

        long withoutColumn = entity.attributes().stream()
                .filter(a -> isBlank(a.columnName()))
                .count();
        if (withoutColumn > 0) {
            blockers.add(withoutColumn + " attribute(s) with no column");
        }

        entity.attributes().stream()
                .map(EntityObject.Attribute::javaType)
                .filter(t -> t != null && Types.isAdfDomainType(t) && Types.toJava(t).equals(t))
                .distinct()
                .forEach(t -> blockers.add("no Java mapping for " + t));

        // A relationship needs its association: the accessor states cardinality but not the
        // target entity or the join columns. Without it there is no mapping to generate.
        entity.accessors().forEach(accessor -> {
            var association = application.associations().get(accessor.association());
            if (association == null) {
                blockers.add("association not found for " + accessor.name());
            } else if (!association.isMappable()) {
                blockers.add("incomplete association for " + accessor.name());
            }
        });

        return new GenerationReadiness(entity.fqn(), Target.JPA_ENTITY, List.copyOf(blockers));
    }

    /** What a repository query needs: something to query, and something to project into. */
    static GenerationReadiness forViewObject(ViewObject view) {
        List<String> blockers = new ArrayList<>();

        if (isBlank(view.sql()) && view.entityUsages().isEmpty()) {
            blockers.add("no query and no entity to read from");
        }
        if (view.attributes().isEmpty()) {
            blockers.add("no attributes to project");
        }

        return new GenerationReadiness(view.fqn(), Target.REPOSITORY_QUERY, List.copyOf(blockers));
    }

    /** What a controller method needs: a resolved chain from URL down to a query. */
    static GenerationReadiness forEndpoint(Endpoint endpoint) {
        List<String> blockers = new ArrayList<>(endpoint.unresolved());
        if (endpoint.viewObject() == null && blockers.isEmpty()) {
            blockers.add("no view object behind the resource");
        }
        return new GenerationReadiness(endpoint.url(), Target.REST_ENDPOINT, List.copyOf(blockers));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
