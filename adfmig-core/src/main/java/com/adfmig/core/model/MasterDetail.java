package com.adfmig.core.model;

import java.util.List;

/**
 * A nested collection ADF published beneath a parent row.
 *
 * <p>ADF BC REST serves these at {@code /{parent}/{id}/child/{accessor}} and returns the same
 * paged envelope a top-level collection returns. They are part of the published contract even
 * though no resource definition mentions them — the wiring lives in the application module's view
 * link usages — so an application that serves only the top-level collections quietly offers less
 * than the one it replaces.
 *
 * @param accessor              the name the child is reached by, which forms the URL segment
 * @param masterJoinAttributes  attributes on the parent side of the join
 * @param detailJoinAttributes  the matching attributes on the child side
 */
public record MasterDetail(
        Endpoint masterEndpoint,
        String accessor,
        ViewObject detailView,
        EntityObject detailEntity,
        List<String> masterJoinAttributes,
        List<String> detailJoinAttributes,
        String sourcePath) {

    /** The URL ADF served this at. */
    public String url() {
        return masterEndpoint.url() + "/{id}/child/" + accessor;
    }

    /**
     * Whether a filtered query can be written for this child.
     *
     * <p>An entity-backed child can be filtered on its join column. A child whose view carries
     * hand-written SQL cannot: narrowing it would mean editing the developer's query, and a
     * generator that rewrites SQL changes behaviour nothing describes.
     */
    public boolean isGeneratable() {
        return detailEntity != null
                && detailJoinAttributes.size() == 1
                && masterJoinAttributes.size() == 1
                && detailEntity.attribute(detailJoinAttributes.get(0)).isPresent();
    }

    /** Why this child could not be generated, for reporting. */
    public String blocker() {
        if (detailEntity == null) {
            return "the child view has no entity behind it, only a query, so it cannot be "
                    + "narrowed to a parent without editing that query";
        }
        if (detailJoinAttributes.size() != 1 || masterJoinAttributes.size() != 1) {
            return "the join uses " + detailJoinAttributes.size() + " columns, and only a "
                    + "single-column join is generated";
        }
        return "the join column " + String.join(", ", detailJoinAttributes)
                + " was not found on " + detailEntity.simpleName();
    }
}
