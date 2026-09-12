package com.adfmig.core.model;

import java.util.List;

/**
 * An ADF view object: a query over one or more entity objects, plus the operations exposed on it.
 *
 * <p>Migration target: a Spring Data repository with a projection, or a native query when the
 * view object carries expert-mode SQL.
 *
 * @param fqn            fully qualified name
 * @param selectList     the SELECT clause as ADF stores it, for a generated query
 * @param fromList       the FROM clause, for a generated query
 * @param customSql      the complete hand-written statement, when the view object is in expert
 *                       mode. ADF stores this in a {@code SQLQuery} block instead of populating
 *                       {@code selectList} and {@code fromList}, so a parser reading only those
 *                       attributes sees an empty query where in fact there is bespoke SQL —
 *                       frequently joining tables the entity model never mentions.
 * @param customQuery    true when the developer replaced the generated SQL by hand. Such a view
 *                       object cannot be regenerated from its attributes; the SQL must be carried
 *                       across as-is.
 * @param componentClass custom {@code ViewObjectImpl} subclass, or {@code null}
 * @param entityUsages   entity objects this view reads and writes, by fully qualified name
 * @param attributes     projected attributes
 * @param variables      bind variables
 * @param criteria       named view criteria, the declarative filters
 * @param clientMethods  methods published on the client interface — the operations a remote caller
 *                       can actually invoke
 * @param sourcePath     path of the defining XML, relative to the application root
 */
public record ViewObject(
        String fqn,
        String selectList,
        String fromList,
        String customSql,
        boolean customQuery,
        String componentClass,
        List<EntityUsage> entityUsages,
        List<Attribute> attributes,
        List<Variable> variables,
        List<Criteria> criteria,
        List<ClientMethod> clientMethods,
        String sourcePath,
        /**
         * The view object's WHERE clause, verbatim, or null.
         *
         * <p>This is what the query means. It decides which rows the application showed — a
         * stock report that lists only what is below its reorder point, an order list narrowed
         * to one customer — and it is written in SQL against entity aliases rather than against
         * the database's own table names.
         */
        String whereClause) {

    /** True when ADF narrowed this query and the narrowing is not just a join predicate. */
    public boolean hasWhereClause() {
        return whereClause != null && !whereClause.isBlank()
                && !whereClause.trim().matches("1\\s*=\\s*1");
    }

    public String simpleName() {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    /** True when the view object has hand-written Java behind it. */
    public boolean hasCustomImplementation() {
        return componentClass != null && !componentClass.isBlank();
    }

    /**
     * True when the view is not backed by an entity object, so it can be read but not written.
     *
     * <p>These migrate to a native query returning a projection, not to a JPA entity — a
     * materially cheaper and lower-risk path, and worth counting separately when estimating.
     */
    public boolean isReadOnly() {
        return entityUsages.isEmpty();
    }

    /**
     * The query this view runs: the hand-written statement when in expert mode, otherwise the
     * clauses ADF generated from the attribute list.
     */
    public String sql() {
        if (customSql != null && !customSql.isBlank()) return customSql;
        if (selectList == null && fromList == null) return null;
        return "SELECT " + selectList + " FROM " + fromList;
    }

    /**
     * The query including its WHERE clause, for reading rows the way ADF read them.
     *
     * <p>{@link #sql()} leaves the clause off, which is right where it is only used to describe
     * the shape of a query. It is wrong for running one: the clause is what decides which rows
     * come back. Kept separate so the two uses cannot be confused.
     *
     * <p>The text is ADF's own, unchanged — including Oracle's {@code (+)}, the functions and the
     * subqueries that have no equivalent in JPQL. Carrying it over verbatim is the point: it runs
     * against the same database that ran it before.
     */
    public String filteredSql() {
        String base = sql();
        if (base == null) return null;
        if (customSql != null && !customSql.isBlank()) return base;   // already whole
        if (whereClause == null || whereClause.isBlank()) return base;
        return base + " WHERE " + whereClause;
    }

    /** @param entity fully qualified name of the entity object */
    public record EntityUsage(String name, String entity) {}

    /**
     * @param entityAttrName the entity attribute this projects, or {@code null} for a
     *                       transient or SQL-derived attribute
     * @param aliasName      the column alias in the query
     */
    public record Attribute(
            String name,
            String entityAttrName,
            String entityUsage,
            String aliasName,
            String javaType,
            boolean notNull,
            boolean unique) {

        /** True when the attribute has no backing entity column — calculated or SQL-only. */
        public boolean isTransient() {
            return entityAttrName == null || entityAttrName.isBlank();
        }
    }

    /** A bind variable. {@code kind} is typically {@code viewcriteria} or {@code where}. */
    public record Variable(String name, String javaType, String kind) {}

    /** A named declarative filter. Migration target: a JPA {@code Specification}. */
    public record Criteria(String name, String conjunction, List<CriteriaItem> items) {}

    /**
     * One condition within a view criteria.
     *
     * @param value       the compared value, or {@code :bindVariable} when bound
     * @param bindVariable true when {@code value} names a bind variable rather than a literal
     * @param required    ADF's {@code Required} setting: Optional, Selectively or Required
     */
    public record CriteriaItem(
            String attribute,
            String operator,
            String value,
            String conjunction,
            boolean bindVariable,
            String required) {}

    /** A method published on the client interface, callable remotely. */
    public record ClientMethod(String name, String returnType, List<Param> params) {

        public String signature() {
            String args = params.stream()
                    .map(p -> p.name() + ": " + Types.simple(p.javaType()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            return name + "(" + args + "): " + Types.simple(returnType);
        }
    }

    public record Param(String name, String javaType) {}
}
