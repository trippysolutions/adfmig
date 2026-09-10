package com.adfmig.core.analysis;

/**
 * Converts complexity into person-days.
 *
 * <p><strong>These weights are uncalibrated.</strong> They are a defensible starting point, not a
 * measurement: no migration has been completed with this tool yet, so nothing here is derived from
 * actuals. Replace them with recorded effort after the first project, and say so on any estimate
 * produced before then. A consultant will attack the number, and the only good answer is to show
 * the formula and its provenance.
 *
 * <p>The shape is {@code base + complexity × rate}, chosen per migration class: a class both
 * shifts the baseline and changes how quickly cost grows with complexity, because the expensive
 * part of hand-written code is understanding it, which scales worse than generating it.
 */
public record EffortModel(
        double autoBase, double autoRate,
        double assistedBase, double assistedRate,
        double manualBase, double manualRate,
        double applicationSetup,
        double securityBase,
        double perRole,
        double perEndpointTest,
        double perPageDefinitionRewrite) {

    /** Starting weights, pending calibration against a completed migration. */
    public static EffortModel uncalibrated() {
        return new EffortModel(
                0.10, 0.004,   // generated: review the output, little more
                0.50, 0.020,   // generated then finished by hand
                1.50, 0.050,   // read, understand and rewrite against the new model
                5.0,           // project scaffolding, build, pipeline, configuration
                2.0, 0.5,      // security configuration, plus each distinct role
                0.25,          // a contract test per endpoint, old response against new
                1.5);          // one ADF Faces page definition rebuilt as a UI screen
    }

    public double days(MigrationClass migrationClass, int complexity) {
        return switch (migrationClass) {
            case AUTO -> autoBase + complexity * autoRate;
            case ASSISTED -> assistedBase + complexity * assistedRate;
            case MANUAL -> manualBase + complexity * manualRate;
            case REWRITE -> perPageDefinitionRewrite;
        };
    }

    /** The formula, written out so an estimate can be argued with rather than just disbelieved. */
    public String formula(MigrationClass migrationClass) {
        return switch (migrationClass) {
            case AUTO -> "%.2f + complexity x %.3f".formatted(autoBase, autoRate);
            case ASSISTED -> "%.2f + complexity x %.3f".formatted(assistedBase, assistedRate);
            case MANUAL -> "%.2f + complexity x %.3f".formatted(manualBase, manualRate);
            case REWRITE -> "%.2f per page definition".formatted(perPageDefinitionRewrite);
        };
    }
}
