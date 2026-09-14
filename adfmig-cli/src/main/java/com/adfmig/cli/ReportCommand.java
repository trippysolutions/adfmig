package com.adfmig.cli;

import com.adfmig.core.analysis.ApplicationAssessment;
import com.adfmig.core.analysis.Assessor;
import com.adfmig.core.analysis.EffortModel;
import com.adfmig.core.analysis.MigrationClass;
import com.adfmig.core.model.AdfApplication;
import com.adfmig.parser.AdfApplicationParser;
import com.adfmig.parser.ApplicationDiscovery;
import com.adfmig.report.HtmlReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Produces the migration assessment: what an application will cost to move to Spring Boot, and why.
 */
@Command(
        name = "report",
        mixinStandardHelpOptions = true,
        description = "Assess migration cost and write a self-contained HTML report.")
public final class ReportCommand implements Callable<Integer> {

    @Parameters(paramLabel = "PATH", arity = "1..*", description = "Root directory of an ADF application.")
    private List<Path> roots;

    @Option(names = {"-o", "--out"}, description = "Directory to write reports into (default: ./reports).")
    private Path outDir = Path.of("reports");

    @Option(names = "--all", description = "Assess every application under the path, each on its own.")
    private boolean all;

    @Option(names = "--json", description = "Also write the assessment as JSON alongside each report.")
    private boolean json;


    @Override
    public Integer call() throws Exception {

        PrintStream out = System.out;
        for (Path path : roots) {
            if (!Files.isDirectory(path)) {
                System.err.println("not a directory: " + path);
                return 2;
            }
        }

        AdfApplicationParser parser = new AdfApplicationParser();
        Assessor assessor = new Assessor(EffortModel.uncalibrated());
        HtmlReport html = new HtmlReport();
        Files.createDirectories(outDir);

        double backendTotal = 0;
        int assessed = 0;
        // Applications routinely share a name inside one estate — a repository of samples had
        // nine collisions across 270 applications. Writing them all to the same filename loses
        // reports silently, which is the worst way to lose them.
        Set<String> usedNames = new LinkedHashSet<>();

        for (Path root : expand(roots)) {
            AdfApplication application;
            try (var progress = Terminal.Progress.on(out, "Reading " + root.getFileName())) {
                application = parser.parse(root, progress::update);
                progress.done(application.entities().size() + " entities, "
                        + application.viewObjects().size() + " view objects");
            }
            ApplicationAssessment assessment = assessor.assess(application);

            String base = uniqueName(fileName(assessment.name()), usedNames);
            Path file = outDir.resolve(base + ".html");
            Files.writeString(file, html.render(assessment), StandardCharsets.UTF_8);

            if (json) {
                ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(outDir.resolve(base + ".json").toFile(), assessment.artifacts());
            }

            summarise(out, assessment, file);
            backendTotal += assessment.backendDays();
            assessed++;
        }

        if (assessed > 1) {
            out.printf("%n%d applications assessed, %.0f person-days total (backend).%n",
                    assessed, backendTotal);
        }
        return 0;
    }

    /**
     * The console half of a report: the verdict first, then the arithmetic behind it.
     *
     * <p>Someone running this is deciding whether to start. That decision is made on whether the
     * application can be migrated at all, so that goes at the top, in words, before any number.
     */
    private void summarise(PrintStream out, ApplicationAssessment a, Path file) {
        out.println();
        out.println("=".repeat(88));
        out.println("  " + a.name());
        out.println("=".repeat(88));

        Verdict.of(a.application()).print(out);

        if (a.hasBusinessModel()) {
            out.println();
            out.println("  EFFORT");
            out.println();
            Table effort = a.pageDefinitions() > 0
                    ? Table.of("WORK", "PERSON-DAYS", "NOTE").right(1)
                    : Table.of("WORK", "PERSON-DAYS").right(1);
            if (a.pageDefinitions() > 0) {
                effort.row("Backend migration", "%.1f".formatted(a.backendDays()), "");
                effort.row("Front end rebuild", "%.0f".formatted(a.frontEndRebuildDays()),
                        "a separate project — " + a.pageDefinitions() + " page definitions");
            } else {
                effort.row("Backend migration", "%.1f".formatted(a.backendDays()));
            }
            effort.print(out, "    ");

            out.println();
            out.println("  COMPONENTS");
            out.println();
            Table components = Table.of("COUNT", "CLASS", "WHAT THAT MEANS").right(0);
            a.countsByClass().forEach((migrationClass, count) -> components.row(
                    count, migrationClass.name().toLowerCase(Locale.ROOT), migrationClass.description()));
            components.print(out, "    ");

            int generated = a.countsByClass().getOrDefault(MigrationClass.AUTO, 0)
                    + a.countsByClass().getOrDefault(MigrationClass.ASSISTED, 0);
            int total = a.artifacts().size();
            if (total > 0) {
                out.printf("%n    %d%% of components are generated, in whole or as a starting point.%n",
                        Math.round(generated * 100.0 / total));
            }
        }

        out.println();
        out.printf("  Report: %s%n", Terminal.cyan(file.toAbsolutePath().toString()));
    }

    private List<Path> expand(List<Path> paths) throws Exception {
        if (!all) return paths;
        ApplicationDiscovery discovery = new ApplicationDiscovery();
        List<Path> expanded = new ArrayList<>();
        for (Path path : paths) {
            discovery.discover(path).forEach(f -> expanded.add(f.root()));
        }
        return expanded;
    }

    /** Keeps every report, even when several applications in an estate share a name. */
    private static String uniqueName(String base, Set<String> used) {
        if (used.add(base)) return base;
        for (int suffix = 2; ; suffix++) {
            String candidate = base + "-" + suffix;
            if (used.add(candidate)) return candidate;
        }
    }

    /** Application names come from workspace files and may contain anything a filename cannot. */
    private static String fileName(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return safe.isEmpty() ? "assessment" : safe;
    }
}
