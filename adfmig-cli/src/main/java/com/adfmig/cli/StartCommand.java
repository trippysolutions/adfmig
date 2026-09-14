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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
            String answer = ask("  path> ").strip();
            if (answer.isBlank()) return null;

            Path path = resolve(answer);
            if (Files.isDirectory(path)) return path;

            out.println("  " + Terminal.red("Not a directory: ") + path);
            adviceFor(answer, path).forEach(line -> out.println("  " + Terminal.dim(line)));
        }
    }

    /** Expands a leading {@code ~} the way a shell would, since nothing expands it for us here. */
    static Path resolve(String typed) {
        String expanded = typed.startsWith("~")
                ? System.getProperty("user.home") + typed.substring(1)
                : typed;
        return Path.of(expanded).normalize();
    }

    /**
     * What to say when the path does not exist.
     *
     * <p>"Not a directory" is true and useless. Two mistakes account for nearly every failure at
     * this prompt: a typo in one segment, and an absolute path where the person meant one under
     * their home directory. Both are visible from here, so both get answered by name rather than
     * left for the user to find. The first prompt of the tool is the worst possible place to
     * strand someone.
     */
    static List<String> adviceFor(String typed, Path path) {
        List<String> advice = new ArrayList<>();

        // The same text read from the home directory instead of the disk root. Checked first
        // because it is the mistake that leaves the typed path looking entirely reasonable.
        Path underHome = typed.startsWith("~")
                ? null
                : Path.of(System.getProperty("user.home"), typed).normalize();
        if (underHome != null && Files.isDirectory(underHome)) {
            advice.add("Did you mean  " + underHome + "  ?");
            return advice;
        }

        Path home = Path.of(System.getProperty("user.home"));
        for (Path candidate : underHome == null ? List.of(path) : List.of(path, underHome)) {
            Path existing = deepestExisting(candidate);
            if (existing == null || existing.equals(candidate)) continue;
            if (candidate.getNameCount() <= existing.getNameCount()) continue;
            // Joining an absolute path onto the home directory only means something if it starts
            // to exist there. Without this, any unrecognised path produces a suggestion built
            // from whatever happens to sit in the home directory, which is worse than silence.
            if (candidate == underHome && existing.getNameCount() <= home.getNameCount()) continue;
            String missing = candidate.getName(existing.getNameCount()).toString();
            for (String near : nearest(existing, missing)) {
                String suggestion = "Did you mean  " + existing.resolve(near) + "  ?";
                if (!advice.contains(suggestion)) advice.add(suggestion);
            }
        }

        // Only worth explaining when the path really does begin somewhere that does not exist.
        // A deep path under a directory that is plainly there went wrong further along, and the
        // person does not need a lesson about the disk root to hear it.
        if (advice.isEmpty() && typed.startsWith("/") && path.getNameCount() > 0
                && !Files.exists(path.getRoot().resolve(path.getName(0)))) {
            advice.add("A path starting with / is read from the root of the disk.");
            advice.add("For a directory inside your home folder, start with  ~/");
        }
        return advice;
    }

    /** The deepest part of the path that does exist, which is where a typo starts. */
    private static Path deepestExisting(Path path) {
        for (Path at = path; at != null; at = at.getParent()) {
            if (Files.isDirectory(at)) return at;
        }
        return null;
    }

    /** Directory names in {@code directory} close enough to {@code missing} to be worth offering. */
    private static List<String> nearest(Path directory, String missing) {
        if (missing.isBlank()) return List.of();
        try (var entries = Files.list(directory)) {
            return entries.filter(Files::isDirectory)
                    .map(entry -> entry.getFileName().toString())
                    .filter(name -> close(name, missing))
                    .sorted(Comparator.comparingInt(name -> distance(name, missing)))
                    .limit(3)
                    .toList();
        } catch (Exception unreadable) {
            // A directory we cannot list simply produces no suggestion; the error above already
            // said what went wrong.
            return List.of();
        }
    }

    private static boolean close(String name, String missing) {
        String a = name.toLowerCase(Locale.ROOT);
        String b = missing.toLowerCase(Locale.ROOT);
        return a.startsWith(b) || b.startsWith(a) || distance(name, missing) <= 2;
    }

    /** Levenshtein distance, case-insensitively — enough to catch a dropped or doubled letter. */
    static int distance(String left, String right) {
        String a = left.toLowerCase(Locale.ROOT);
        String b = right.toLowerCase(Locale.ROOT);
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitute = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitute, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private AdfEstate survey(PrintStream out, Path root) throws Exception {
        out.println();
        try (var progress = Terminal.Progress.on(out, "Surveying")) {
            AdfEstate estate = new EstateParser().parse(root, progress::update);
            progress.done(estate.applications().size() + " application(s)");
            return estate;
        }
    }

    /**
     * What the estate contains, as counts rather than rows.
     *
     * <p>Listing every application here was printing hundreds of lines and then asking the person
     * to pick a number from a list that had already scrolled away. The counts are what this step
     * is for; choosing happens in the next one, against a list small enough to still be on screen.
     */
    private void showEstate(PrintStream out, AdfEstate estate) {
        out.println();
        out.printf("  %s %s%n", Terminal.bold("2. What is there"),
                Terminal.dim("— " + estate.applications().size() + " application(s)"));
        out.println();
        Table counts = Table.of("APPLICATIONS", "HOW IT IS CONSUMED").right(0);
        estate.byProfile().forEach((profile, applications) ->
                counts.row(applications.size(), colour(profile)));
        counts.print(out, "    ");

        var conflicts = estate.schemaConflicts();
        if (!conflicts.isEmpty()) {
            out.println();
            out.printf("  %s %d schema(s) are written by more than one application:%n",
                    Terminal.yellow("!"), conflicts.size());
            // A few, then a count. This sits immediately above the prompt, and a long list here
            // pushes the question the person is meant to answer off the top of the screen.
            out.println();
            Table shared = Table.of("SCHEMA OR DATASOURCE", "APPLICATIONS").width(0, 46).right(1);
            conflicts.entrySet().stream().limit(5).forEach(conflict ->
                    shared.row(conflict.getKey(), conflict.getValue().size()));
            shared.print(out, "      ");
            if (conflicts.size() > 5) {
                out.println("      " + Terminal.dim("... and " + (conflicts.size() - 5)
                        + " more — see  adfmig apps  for all of them"));
            }
            out.println();
            out.println("  " + Terminal.dim("Migrate these together, or agree a locking strategy first."));
        }
        if (!estate.credentialFindings().isEmpty()) {
            out.println();
            out.printf("  %s %d file(s) contain credentials. Rotate them; they reverse.%n",
                    Terminal.yellow("!"), estate.credentialFindings().size());
        }
    }

    /** How many applications to put on screen at once, so the prompt stays visible under them. */
    private static final int PAGE = 12;

    /**
     * Asks which application to assess, against a shortlist rather than the whole estate.
     *
     * <p>Numbers always index what is currently on screen, so a search replaces the list and
     * renumbers it. Anything else would mean typing a number that refers to output the person
     * cannot see.
     */
    private DiscoveredApplication chooseApplication(PrintStream out, AdfEstate estate) {
        List<DiscoveredApplication> all = estate.applications();

        out.println();
        out.println("  " + Terminal.bold("3. Which one shall we assess?"));
        out.println("  " + Terminal.dim("A number to assess it, a name to search for, "
                + "\"all\" to list every one."));
        out.println("  " + Terminal.dim("Blank to stop."));

        List<DiscoveredApplication> shown =
                show(out, shortlist(all), PAGE, all.size(), "the ones worth migrating first");

        while (true) {
            String answer = ask("  number/name> ").strip();
            if (answer.isBlank()) return null;

            if (answer.equalsIgnoreCase("all")) {
                shown = show(out, all, all.size(), all.size(), "every application found");
                continue;
            }

            Integer index = number(answer);
            if (index != null) {
                if (index >= 1 && index <= shown.size()) return shown.get(index - 1);
                out.println("  " + Terminal.red(
                        "Pick a number between 1 and " + shown.size() + ", from the list above."));
                continue;
            }

            List<DiscoveredApplication> matches = search(all, answer);
            if (matches.isEmpty()) {
                out.println("  " + Terminal.red("Nothing matched \"" + answer + "\""));
                out.println("  " + Terminal.dim("Search matches any part of a name or path."));
                continue;
            }
            shown = show(out, matches, PAGE, all.size(), "matching \"" + answer + "\"");
        }
    }

    /**
     * Prints a numbered list and returns exactly what it printed, so the numbers the person reads
     * and the numbers this command accepts can never drift apart.
     */
    private List<DiscoveredApplication> show(PrintStream out, List<DiscoveredApplication> applications,
                                             int limit, int total, String what) {
        List<DiscoveredApplication> page = applications.stream().limit(limit).toList();

        out.println();
        Table table = Table.of("#", "APPLICATION", "EO", "VO", "REST", "KIND")
                .width(1, 34).tail(1)
                .right(0, 2, 3, 4);
        for (int i = 0; i < page.size(); i++) {
            DiscoveredApplication application = page.get(i);
            table.row(i + 1, application.path(),
                    application.entityObjects(), application.viewObjects(),
                    application.restResources(),
                    kind(application.profile()));
        }
        table.print(out, "     ");

        out.println();
        out.println("     " + Terminal.dim(page.size() < applications.size()
                ? "Showing " + page.size() + " of " + applications.size() + " — " + what
                        + ". Type part of a name to narrow it."
                : page.size() + " of " + total + " — " + what + "."));
        return page;
    }

    /**
     * The applications worth offering first: those that already publish REST, then the ones with
     * the most business components. Applications with no business model are left out entirely —
     * there is nothing in them to assess.
     */
    static List<DiscoveredApplication> shortlist(List<DiscoveredApplication> all) {
        List<DiscoveredApplication> worthwhile = all.stream()
                .filter(a -> a.profile() != DiscoveredApplication.Profile.NO_BUSINESS_MODEL)
                .sorted(Comparator
                        .comparingInt((DiscoveredApplication a) -> a.profile().ordinal())
                        .thenComparing(DiscoveredApplication::businessComponents,
                                Comparator.reverseOrder()))
                .toList();
        return worthwhile.isEmpty() ? all : worthwhile;
    }

    static List<DiscoveredApplication> search(List<DiscoveredApplication> all, String text) {
        String needle = text.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(a -> a.path().toLowerCase(Locale.ROOT).contains(needle)
                        || a.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    private static Integer number(String answer) {
        try {
            return Integer.valueOf(answer);
        } catch (NumberFormatException notANumber) {
            return null;
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
        out.println("  " + Terminal.bold("4. Can it be migrated?"));

        Verdict.of(assessment.application()).print(out);

        out.println();
        out.println("  " + Terminal.bold("5. What it would take"));
        out.println();
        // Only two columns when there is no front end to rebuild, rather than a NOTE column
        // with nothing under it.
        Table effort = assessment.pageDefinitions() > 0
                ? Table.of("WORK", "PERSON-DAYS", "NOTE").right(1)
                : Table.of("WORK", "PERSON-DAYS").right(1);
        if (assessment.pageDefinitions() > 0) {
            effort.row("Backend migration", "%.0f".formatted(assessment.backendDays()), "");
            effort.row("Front end rebuild", "%.0f".formatted(assessment.frontEndRebuildDays()),
                    "a separate project");
        } else {
            effort.row("Backend migration", "%.0f".formatted(assessment.backendDays()));
        }
        effort.print(out, "    ");

        out.println();
        Table components = Table.of("COUNT", "CLASS", "WHAT THAT MEANS").right(0);
        assessment.countsByClass().forEach((migrationClass, count) -> components.row(
                count, migrationClass.name().toLowerCase(java.util.Locale.ROOT),
                migrationClass.description()));
        components.print(out, "    ");

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
        out.println("  " + Terminal.bold("6. Generate the Spring Boot project?"));

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

    /**
     * The profile in a few words rather than a sentence.
     *
     * <p>The full description is a sentence, and a table carrying it is a hundred and twenty
     * columns wide — so on a normal window every row wraps and the table stops being one. The
     * sentence is already given once, above, in the counts.
     */
    private static String kind(DiscoveredApplication.Profile profile) {
        return switch (profile) {
            case REST_CONTRACT -> Terminal.green("REST contract");
            case ADF_FACES_UI -> Terminal.yellow("ADF Faces UI");
            case MODEL_ONLY -> Terminal.dim("shared library");
            case NO_BUSINESS_MODEL -> Terminal.dim("no model");
        };
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
