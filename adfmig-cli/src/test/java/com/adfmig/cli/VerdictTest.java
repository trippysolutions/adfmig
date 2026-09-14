package com.adfmig.cli;

import com.adfmig.core.analysis.GenerationReadiness;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Where the thresholds sit, which is the whole of what a verdict is. */
class VerdictTest {

    @Test
    void saysThereIsNothingToMigrateWhenNothingWasFound() {
        Verdict verdict = Verdict.from(List.of());

        assertThat(verdict.level()).isEqualTo(Verdict.Level.NOTHING);
        assertThat(verdict.detail()).contains("not one with a business model");
    }

    @Test
    void saysReadyOnlyWhenEveryComponentIs() {
        assertThat(Verdict.from(checks(5, 0)).level()).isEqualTo(Verdict.Level.READY);
        assertThat(Verdict.from(checks(4, 1)).level()).isEqualTo(Verdict.Level.REVIEW);
    }

    @Test
    void callsItBlockedOnlyWhenFewerThanHalfCanBeGenerated() {
        assertThat(Verdict.from(checks(5, 5)).level()).isEqualTo(Verdict.Level.REVIEW);
        assertThat(Verdict.from(checks(4, 6)).level()).isEqualTo(Verdict.Level.BLOCKED);
    }

    @Test
    void countsWhatIsReadyAgainstTheWhole() {
        Verdict verdict = Verdict.from(checks(7, 3));

        assertThat(verdict.ready()).isEqualTo(7);
        assertThat(verdict.total()).isEqualTo(10);
    }

    @Test
    void putsTheCommonestBlockerFirstAndFoldsAwayTheCounts() {
        List<GenerationReadiness> checks = new ArrayList<>();
        checks.add(blocked("a", "no primary key"));
        checks.add(blocked("b", "no primary key"));
        checks.add(blocked("c", "3 attribute(s) with no column"));
        checks.add(blocked("d", "7 attribute(s) with no column"));
        checks.add(blocked("e", "7 attribute(s) with no column"));
        checks.add(blocked("f", "7 attribute(s) with no column"));

        assertThat(Verdict.from(checks).blockers())
                .first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("N attribute(s) with no column")
                .endsWith("4");
    }

    private static List<GenerationReadiness> checks(int ready, int blocked) {
        List<GenerationReadiness> checks = new ArrayList<>();
        for (int i = 0; i < ready; i++) {
            checks.add(new GenerationReadiness("ok" + i,
                    GenerationReadiness.Target.JPA_ENTITY, List.of()));
        }
        for (int i = 0; i < blocked; i++) checks.add(blocked("bad" + i, "no primary key"));
        return checks;
    }

    private static GenerationReadiness blocked(String fqn, String blocker) {
        return new GenerationReadiness(fqn, GenerationReadiness.Target.JPA_ENTITY, List.of(blocker));
    }
}
