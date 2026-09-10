package com.adfmig.cli;

import com.adfmig.core.analysis.ApplicationAssessment;
import com.adfmig.core.analysis.Assessor;
import com.adfmig.core.analysis.EffortModel;
import com.adfmig.core.analysis.MigrationClass;
import com.adfmig.core.estate.AdfEstate;
import com.adfmig.core.estate.DiscoveredApplication;
import com.adfmig.core.model.AdfApplication;
import com.adfmig.parser.AdfApplicationParser;
import com.adfmig.parser.EstateParser;
import com.adfmig.report.HtmlReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.Console;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Walks someone through a first migration.
 *
 * <p>The commands are the interface for anyone who already knows what they want. This is for the
 * person who has just unzipped the tool, has a directory of ADF somewhere, and does not yet know
 * that the first question is whether their applications publish REST. It asks that question by
 * answering it.
 *
 * <p>It refuses to run without a terminal. A wizard reading from a pipe would hang waiting for an
 * answer nobody is there to give.
 */
@Command(
        name = "start",
        mixinStandardHelpOptions = true,
        description = "Walk through surveying, assessing and migrating an application.")
public final class StartCommand implements Callable<Integer> {

    private Console console;
    private Optional<ProExtension> extension = Optional.empty();

    @Override
    public Integer call() throws Exception {
        console = System.console();
        if (console == null || !Terminal.isInteractive()) {
            System.err.println("'adfmig start' needs a terminal. Use the individual commands "
                    + "instead: adfmig apps, adfmig report, adfmig generate.");
            return 2;
        }

        extension = ProExtension.find();
        PrintStream out = System.out;

        banner(out);

        Path estateRoot = askForEstate(out);
        if (estateRoot == null) return 0;

        AdfEstate estate = survey(out, estateRoot);
        if (estate.applications().isEmpty()) {
            out.println("  No ADF applications found there.");
            return 0;
        }

        showEstate(out, estate);

        DiscoveredApplication chosen = chooseApplication(out, estate);
        if (chosen == null) return 0;

        Path applicationRoot = Path.of(estate.root()).resolve(chosen.path());
        ApplicationAssessment assessment = assess(out, applicationRoot);
        showAssessment(out, assessment);

        offerToGenerate(out, applicationRoot, assessment);
        nextSteps(out, assessment);
        return 0;
    }

    // --- steps -------------------------------------------------------------------------

    private void banner(PrintStream out) {
        Branding.banner(out);
        extension.ifPresent(installed -> out.println("  " + Terminal.dim(installed.describe())));
        out.println();
        out.println("  This walks through one migration: find your applications, assess one, and");
        out.println("  generate its replacement. Nothing leaves this machine.");
    }

    private Path askForEstate(PrintStream out) {
        out.println();
        out.println("  " + Terminal.bold("1. Where is your ADF source?"));
        out.println("  " + Terminal.dim("A directory holding one or more applications. Blank to stop."));

        while (true) {
            String answer = ask("  path> ");
            if (answer.isBlank()) return null;

            Path path = Path.of(answer.replaceFirst("^~", System.getProperty("user.home")));
            if (Files.isDirectory(path)) return path;
            out.println("  " + Terminal.red("Not a directory: ") + path);
        }
    }

    private AdfEstate survey(PrintStream out, Path root) throws Exception {
        out.println();
        try (var progress = Terminal.Progress.on(out, "Surveying")) {
            AdfEstate estate = new EstateParser().parse(root, progress::update);
            progress.done(estate.applications().size() + " application(s)");
            return estate;
        }
    }

    private void showEstate(PrintStream out, AdfEstate estate) {
        out.println();
        out.println("  " + Terminal.bold("2. What is there"));
        out.println();
        List<DiscoveredApplication> applications = estate.applications();
        for (int i = 0; i < applications.size(); i++) {
            DiscoveredApplication application = applications.get(i);
            out.printf("    %2d. %-44s %s%n", i + 1,
                    truncate(application.path(), 44), colour(application.profile()));
        }

        var conflicts = estate.schemaConflicts();
        if (!conflicts.isEmpty()) {
            out.println();
            out.println("  " + Terminal.yellow("Applications sharing a database schema:"));
            conflicts.forEach((schema, apps) ->
                    out.printf("    %s  <- %d applications%n", schema, apps.size()));
            out.println("  " + Terminal.dim("Migrate these together, or agree a locking strategy first."));
        }
        if (!estate.credentialFindings().isEmpty()) {
            out.println();
            out.printf("  %s %d file(s) contain credentials. Rotate them; they reverse.%n",
                    Terminal.yellow("!"), estate.credentialFindings().size());
        }
    }

    private DiscoveredApplication chooseApplication(PrintStream out, AdfEstate estate) {
        List<DiscoveredApplication> applications = estate.applications();
        out.println();
        out.println("  " + Terminal.bold("3. Which one shall we assess?"));

        while (true) {
            String answer = ask("  number> ");
            if (answer.isBlank()) return null;
            try {
                int index = Integer.parseInt(answer.trim());
                if (index >= 1 && index <= applications.size()) return applications.get(index - 1);
            } catch (NumberFormatException ignored) {
                // Fall through to the same message; a typo and an out-of-range number need the
                // same answer.
            }
            out.println("  " + Terminal.red("Pick a number between 1 and " + applications.size()));
        }
    }

    private ApplicationAssessment assess(PrintStream out, Path applicationRoot) throws Exception {
        out.println();
        try (var progress = Terminal.Progress.on(out, "Reading " + applicationRoot.getFileName())) {
            AdfApplication application = new AdfApplicationParser().parse(applicationRoot, progress::update);
            progress.done(application.entities().size() + " entities, "
                    + application.viewObjects().size() + " view objects");
            return new Assessor(EffortModel.uncalibrated()).assess(application);
        }
    }

    private void showAssessment(PrintStream out, ApplicationAssessment assessment) throws Exception {
        out.println();
        out.println("  " + Terminal.bold("4. What it would take"));
        out.println();
        out.printf("    %-34s %s%n", "Backend migration",
                Terminal.bold(String.format("%.0f person-days", assessment.backendDays())));
        if (assessment.pageDefinitions() > 0) {
            out.printf("    %-34s %.0f person-days  %s%n", "Front end rebuild",
                    assessment.frontEndRebuildDays(),
                    Terminal.dim("(a separate project)"));
        }
        out.println();
        assessment.countsByClass().forEach((migrationClass, count) ->
                out.printf("    %-14s %4d  %s%n", migrationClass.name().toLowerCase(), count,
                        Terminal.dim(migrationClass.description())));

        out.println();
        out.println("  " + Terminal.dim("Effort weights are uncalibrated until measured against a"));
        out.println("  " + Terminal.dim("completed migration. The report shows the formula."));

        // Output goes where the person ran the tool, under one directory, so a walkthrough never
        // scatters files across their filesystem and never writes inside the installation.
        Path reportDir = outputRoot().resolve("reports");
        Files.createDirectories(reportDir);
        Path report = reportDir.resolve(safe(assessment.name()) + ".html");
        Files.writeString(report, new HtmlReport().render(assessment), StandardCharsets.UTF_8);
        out.println();
        out.println("    Report: " + Terminal.cyan(report.toAbsolutePath().toString()));
    }

    private void offerToGenerate(PrintStream out, Path applicationRoot,
                                 ApplicationAssessment assessment) throws Exception {
        out.println();
        out.println("  " + Terminal.bold("5. Generate the Spring Boot project?"));

        if (extension.isEmpty() || !extension.get().canGenerate()) {
            out.println("  " + Terminal.dim("This is the free assessment tool, which reports what a"));
            out.println("  " + Terminal.dim("migration would take. Generating the replacement is part of"));
            out.println("  " + Terminal.dim("adfmig Pro — " + Branding.WEBSITE));
            return;
        }

        String answer = ask("  [y/N]> ");
        if (!answer.strip().equalsIgnoreCase("y")) return;

        Optional<ProExtension.Summary> summary;
        try (var progress = Terminal.Progress.on(out, "Generating")) {
            progress.update("writing the project");
            summary = extension.get().generate(assessment.application(), outputRoot().resolve("generated"));
            summary.ifPresent(s -> progress.done(s.filesWritten() + " files"));
        }

        summary.ifPresent(produced -> {
            out.println();
            out.println("    Project:   " + Terminal.cyan(produced.projectPath()));
            out.printf("    Checklist: %s  %s%n", Terminal.cyan(produced.checklistPath()),
                    Terminal.dim(produced.needingAttention() + " item(s) need a person"));
        });
    }

    private void nextSteps(PrintStream out, ApplicationAssessment assessment) {
        out.println();
        out.println("  " + Terminal.bold("Next"));
        out.println();
        if (assessment.pageDefinitions() > 0) {
            out.println("    This application is bound to an ADF Faces UI, so the screens have to be");
            out.println("    rebuilt. The generated API is derived from what they read.");
        } else {
            out.println("    This application publishes REST, so the contract is preserved and the");
            out.println("    generated contract tests can prove it against the original.");
        }
        out.println();
        out.println("    " + Terminal.dim("adfmig apps <estate>       survey everything"));
        out.println("    " + Terminal.dim("adfmig report <app>        the assessment on its own"));
        out.println("    " + Terminal.dim("adfmig generate <app>      generate without the walkthrough"));
        out.println();
    }

    // --- helpers -----------------------------------------------------------------------

    /**
     * Where a walkthrough puts what it produces: one directory beside where it was run, named so
     * it is obvious what created it and safe to delete.
     */
    private static Path outputRoot() {
        return Path.of(System.getProperty("user.dir"), "adfmig-output");
    }

    private String ask(String prompt) {
        String answer = console.readLine(prompt);
        return answer == null ? "" : answer;
    }

    private static String colour(DiscoveredApplication.Profile profile) {
        String text = profile.description();
        return switch (profile) {
            case REST_CONTRACT -> Terminal.green(text);
            case ADF_FACES_UI -> Terminal.yellow(text);
            default -> Terminal.dim(text);
        };
    }


    private static String safe(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "assessment" : cleaned;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : "..." + text.substring(text.length() - (max - 3));
    }
}
