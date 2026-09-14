package com.adfmig.core.model;

import java.util.List;
import java.util.Optional;

/**
 * An ADF entity object: one database table and the business rules attached to it.
 *
 * <p>Migration target: a JPA {@code @Entity}.
 *
 * @param fqn             fully qualified name, e.g. {@code com.example.model.entities.Employees}
 * @param dbObjectName    the table or view it maps to
 * @param dbObjectType    {@code table}, {@code view}, {@code synonym}
 * @param rowClass        custom {@code EntityImpl} subclass, or {@code null} when purely declarative.
 *                        Its presence means hand-written lifecycle code that needs review.
 * @param attributes      persistent and transient attributes
 * @param accessors       navigation to related entities, via associations
 * @param constraints     database constraints ADF recorded at design time
 * @param validators      declarative validation rules
 * @param sourcePath      path of the defining XML, relative to the application root
 */
public record EntityObject(
        String fqn,
        String dbObjectName,
        String dbObjectType,
        String rowClass,
        List<Attribute> attributes,
        List<Accessor> accessors,
        List<Constraint> constraints,
        List<Validator> validators,
        String sourcePath,
        /** The entity this one extends, or null. ADF writes the child's own columns only. */
        String extendsEntity) {

    /** True when ADF declared this entity as extending another. */
    public boolean inherits() {
        return extendsEntity != null && !extendsEntity.isBlank();
    }

    /**
     * The column that says which subtype a row is, where this family of entities has one.
     *
     * <p>ADF declares it on the base entity and every subtype overrides it with the value that
     * identifies that subtype, which is the same arrangement JPA calls single-table inheritance.
     */
    public java.util.Optional<Attribute> discriminator() {
        return attributes.stream()
                .filter(Attribute::discriminator)
                .filter(a -> a.columnName() != null && !a.columnName().isBlank())
                .findFirst();
    }

    /**
     * The value in the discriminator column that identifies a row as this entity, or null where
     * this entity declares none — which is what the base of a family looks like.
     */
    public String discriminatorValue() {
        return discriminator()
                .map(Attribute::defaultValue)
                .filter(value -> !value.isBlank())
                .orElse(null);
    }

    public String simpleName() {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    /** True when the entity has hand-written Java that a generator cannot reproduce from metadata. */
    public boolean hasCustomImplementation() {
        return rowClass != null && !rowClass.isBlank();
    }

    /**
     * The key columns, which are the ones an entity can actually be identified by.
     *
     * <p>An attribute ADF marked as the key but stores nowhere is not one. ADF keys a table on
     * ROWID where nothing else identifies a row, and marks calculated attributes as keys too;
     * neither is a column, so neither can carry {@code @Id}. Counting them here produces an entity
     * that is generated, declares no identifier, and is refused at startup.
     */
    public List<Attribute> primaryKey() {
        return attributes.stream()
                .filter(Attribute::primaryKey)
                .filter(a -> a.columnName() != null && !a.columnName().isBlank())
                .toList();
    }

    public Optional<Attribute> attribute(String name) {
        return attributes.stream().filter(a -> a.name().equals(name)).findFirst();
    }

    /**
     * One column of the table.
     *
     * @param javaType          ADF's declared type. Oracle domain types such as
     *                          {@code oracle.jbo.domain.Date} and {@code oracle.jbo.domain.Number}
     *                          have no JPA equivalent and must be mapped, not copied.
     * @param retrievedOnUpdate the database populates this on write (a trigger, or a change
     *                          indicator used for optimistic locking)
     * @param hasExpression     carries a Groovy expression, defined in the entity's {@code .bcs} file
     */
    public record Attribute(
            String name,
            String columnName,
            String javaType,
            String sqlType,
            String columnType,
            Integer precision,
            Integer scale,
            boolean notNull,
            boolean unique,
            boolean primaryKey,
            boolean retrievedOnUpdate,
            boolean hasExpression,
            /**
             * True when ADF marked this the column that says which subtype a row is. ADF calls it
             * DiscrColumn, and it means exactly what JPA's discriminator column means.
             */
            boolean discriminator,
            /**
             * The value ADF defaults this attribute to. On a subtype's discriminator attribute
             * this is the value that identifies the subtype — 'M' for a Man row.
             */
            String defaultValue) {}

    /**
     * Navigation to a related entity through an association.
     *
     * @param cardinality derived from ADF's declared accessor type: {@code oracle.jbo.RowIterator}
     *                    is a collection, {@code oracle.jbo.server.EntityImpl} a single row
     */
    public record Accessor(
            String name,
            String association,
            Cardinality cardinality,
            boolean updateable) {}

    public enum Cardinality {
        /** Maps to {@code @OneToMany} / {@code @ManyToMany}. */
        TO_MANY,
        /** Maps to {@code @ManyToOne} / {@code @OneToOne}. */
        TO_ONE
    }

    /**
     * A database constraint ADF recorded at design time. Useful as a cross-check: a generated
     * entity whose constraints disagree with the live schema signals that the ADF metadata has
     * drifted from the database, which is common in long-lived applications.
     *
     * @param kind           primary key, foreign key, unique or check
     * @param dbName         the constraint's name in the database
     * @param attributes     attributes it covers, by simple name
     * @param referencedKey  for a foreign key, the key it points at
     * @param checkCondition for a check constraint, the SQL condition
     */
    public record Constraint(
            String name,
            Kind kind,
            String dbName,
            List<String> attributes,
            String referencedKey,
            String checkCondition) {

        public enum Kind { PRIMARY_KEY, FOREIGN_KEY, UNIQUE, CHECK, OTHER }
    }

    /**
     * A declarative validation rule.
     *
     * @param type        ADF's validator bean name, e.g. {@code CompareValidationBean}
     * @param onAttribute the attribute validated, or {@code null} for an entity-level rule
     * @param expression  true when the rule delegates to a Groovy expression in the {@code .bcs}
     *                    file rather than being fully declarative — those need translation, not
     *                    a direct annotation
     */
    public record Validator(
            String name,
            String type,
            String onAttribute,
            String compareType,
            boolean expression) {}
}
