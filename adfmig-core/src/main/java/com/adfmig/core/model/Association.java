package com.adfmig.core.model;

import java.util.List;
import java.util.Optional;

/**
 * A relationship between two entity objects.
 *
 * <p>This is what a JPA relationship is generated from. An entity's accessor names the association
 * and its cardinality, but not the columns to join on or the entity at the other end — those live
 * here, so an accessor whose association cannot be resolved cannot become a mapped relationship.
 *
 * <p>ADF states cardinality per end: {@code 1} for the single side and {@code -1} for the
 * collection side. A foreign key from employees to jobs therefore has a {@code 1} end owned by
 * Jobs and a {@code -1} end owned by Employees, which generates {@code @ManyToOne} on Employees
 * and {@code @OneToMany} on Jobs.
 */
public record Association(String fqn, List<End> ends, String sourcePath) {

    public String simpleName() {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    /** The single-valued end, which owns the referenced key. */
    public Optional<End> toOneEnd() {
        return ends.stream().filter(End::isSingle).findFirst();
    }

    /** The collection end, which holds the foreign key column. */
    public Optional<End> toManyEnd() {
        return ends.stream().filter(e -> !e.isSingle()).findFirst();
    }

    /** True when both ends and their join attributes are present, so a mapping can be generated. */
    public boolean isMappable() {
        return ends.size() == 2 && ends.stream().allMatch(e ->
                e.owner() != null && !e.attributes().isEmpty());
    }

    /**
     * One side of the relationship.
     *
     * @param cardinality ADF's raw value: {@code 1} for a single row, {@code -1} for many
     * @param owner       fully qualified name of the entity object at this end
     * @param source      true when this end is the association's declared source
     * @param attributes  the attributes joined on, by simple name — the join columns
     * @param foreignKey  the database constraint ADF recorded for this end, when it did
     */
    public record End(
            String name,
            String cardinality,
            String owner,
            boolean source,
            List<String> attributes,
            String foreignKey) {

        /** True when this end refers to a single row rather than a collection. */
        public boolean isSingle() {
            return !"-1".equals(cardinality);
        }
    }
}
