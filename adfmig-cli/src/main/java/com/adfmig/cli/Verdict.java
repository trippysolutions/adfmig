package com.adfmig.cli;

import com.adfmig.core.analysis.GenerationReadiness;
import com.adfmig.core.model.AdfApplication;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one sentence someone is actually asking for: can this application be migrated, and if not,
 * what is in the way.
 *
 * <p>Counts, percentages and person-days all answer narrower questions. Someone deciding whether to
 * start needs the answer first and the arithmetic afterwards, stated plainly enough that they can
 * repeat it to somebody who will never run this tool.
 */
record Verdict(Level level, String headline, String detail, List<String> blockers, int ready, int total) {

    enum Level {
        /** Every component has what the generator needs. */
        READY("READY TO MIGRATE"),
        /** Generates, but something has to be decided by a person first. */
        REVIEW("MIGRATES WITH REVIEW"),
        /** Enough is missing that generating now would produce an application that is wrong. */
        BLOCKED("NOT READY"),
        /** There are no business components here at all. */
        NOTHING("NOTHING TO MIGRATE");

        private final String label;

        Level(String label) { this.label = label; }

        String label() { return label; }
    }

    /**
     * Judges an application by whether the generator has the facts it needs, not by how much work
     * the migration is. A trivially simple entity with no primary key is unmigratable; a hundred
     * complex ones that all declare their keys are merely expensive.
     */
    static Verdict of(AdfApplication application) {
        return from(GenerationReadiness.of(application));
    }

    /**
     * The judgement itself, separated from the parsing so it can be exercised directly. What
     * makes a verdict right or wrong is where the thresholds sit, and that should not need an
     * application on disk to check.
     */
    static Verdict from(List<GenerationReadiness> checks) {
        if (checks.isEmpty()) {
            return new Verdict(Level.NOTHING, Level.NOTHING.label(),
                    "No entities, view objects or application modules were found here. "
                            + "This is a project, but not one with a business model in it.",
                    List.of(), 0, 0);
        }

        int total = checks.size();
        int ready = (int) checks.stream().filter(GenerationReadiness::isReady).count();
        int blocked = total - ready;
        List<String> blockers = tally(checks);

        if (blocked == 0) {
            return new Verdict(Level.READY, Level.READY.label(),
                    "All " + total + " components carry everything the generator needs. "
                            + "Nothing has to be decided before starting.",
                    blockers, ready, total);
        }
        // Below half, the generated application would be missing so much that shipping it would
        // mislead rather than help, and saying "mostly ready" about it would be the tool lying.
        if (ready * 2 >= total) {
            return new Verdict(Level.REVIEW, Level.REVIEW.label(),
                    blocked + " of " + total + " components need a person to decide something "
                            + "first. The rest generate as they are.",
                    blockers, ready, total);
        }
        return new Verdict(Level.BLOCKED, Level.BLOCKED.label(),
                "Only " + ready + " of " + total + " components can be generated. The source is "
                        + "missing too much to migrate this as it stands.",
                blockers, ready, total);
    }

    /** Blockers by how often they occur, so the one worth fixing first is at the top. */
    private static List<String> tally(List<GenerationReadiness> checks) {
        Map<String, Long> counted = checks.stream()
                .filter(check -> !check.isReady())
                .flatMap(check -> check.blockers().stream())
                .collect(Collectors.groupingBy(Verdict::normalise, LinkedHashMap::new, Collectors.counting()));
        return counted.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> String.format("%-46s %4d", entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Groups blockers that differ only by a count or a name, so the tally reads as a tally. */
    private static String normalise(String blocker) {
        return blocker.replaceAll("^\\d+ ", "N ").replaceAll("'[^']*'", "'..'");
    }

    String coloured() {
        return switch (level) {
            case READY -> Terminal.green(headline);
            case REVIEW -> Terminal.yellow(headline);
            case BLOCKED -> Terminal.red(headline);
            case NOTHING -> Terminal.dim(headline);
        };
    }

    /** Prints the verdict as a banner: the answer, then why, then what is in the way. */
    void print(PrintStream out) {
        out.println();
        out.println("  " + Terminal.bold(coloured())
                + (total == 0 ? "" : "  " + Terminal.dim(ready + " of " + total + " components ready")));
        out.println();
        Text.wrapped(detail, 82).forEach(line -> out.println("    " + line));

        if (!blockers.isEmpty()) {
            out.println();
            out.println("    " + Terminal.dim("What is in the way"));
            blockers.stream().limit(6).forEach(blocker -> out.println("      " + blocker));
            if (blockers.size() > 6) {
                out.println("      " + Terminal.dim("... and " + (blockers.size() - 6) + " more"));
            }
        }
    }
}
