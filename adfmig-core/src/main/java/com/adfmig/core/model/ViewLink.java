package com.adfmig.core.model;

import java.util.List;
import java.util.Optional;

/**
 * A master-detail relationship between two view objects.
 *
 * <p>Where an {@link Association} joins entity objects and becomes a JPA relationship, a view link
 * joins queries and drives what ADF exposes as a nested collection: a department's employees
 * reached from the department rather than filtered from the whole set.
 *
 * @param entityAssociation the association underneath, when the link is entity-based
 * @param ends              the two sides, cardinality {@code 1} for the master and {@code -1} for
 *                          the detail
 */
public record ViewLink(String fqn, String entityAssociation, List<End> ends, String sourcePath) {

    public String simpleName() {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    /** The master side: one row, from which the detail collection hangs. */
    public Optional<End> master() {
        return ends.stream().filter(End::isSingle).findFirst();
    }

    /** The detail side: the collection reached through the link. */
    public Optional<End> detail() {
        return ends.stream().filter(end -> !end.isSingle()).findFirst();
    }

    public boolean isMappable() {
        return ends.size() == 2 && ends.stream().allMatch(e -> e.owner() != null && !e.attributes().isEmpty());
    }

    /** @param owner fully qualified name of the view object at this end */
    public record End(String name, String cardinality, String owner, boolean source,
                      List<String> attributes) {

        public boolean isSingle() {
            return !"-1".equals(cardinality);
        }
    }
}
