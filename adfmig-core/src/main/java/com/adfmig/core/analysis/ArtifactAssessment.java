package com.adfmig.core.analysis;

import java.util.List;

/**
 * What one artifact will cost to migrate, and why.
 *
 * <p>The {@code drivers} are the point. A number a customer cannot interrogate is a number they
 * will not accept, so every score carries the specific facts that produced it — "hand-written SQL
 * joining 2 tables", "3 Groovy validation rules" — rather than an opaque total.
 *
 * @param complexity 0-100, a relative measure within one estate rather than an absolute
 * @param drivers    the findings that raised the score, in the order they were applied
 * @param effortDays estimated person-days, from {@link EffortModel}
 */
public record ArtifactAssessment(
        String fqn,
        Kind kind,
        String sourcePath,
        int complexity,
        MigrationClass migrationClass,
        List<String> drivers,
        double effortDays) {

    public enum Kind { ENTITY_OBJECT, VIEW_OBJECT, APPLICATION_MODULE, REST_ENDPOINT }

    public String simpleName() {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }
}
