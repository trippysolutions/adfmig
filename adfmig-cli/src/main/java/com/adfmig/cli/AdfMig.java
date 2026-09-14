package com.adfmig.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Entry point for the adfmig command line tool. */
@Command(
        name = "adfmig",
        mixinStandardHelpOptions = true,
        versionProvider = AdfMig.BuildVersion.class,
        // Without this the examples wrap mid-sentence at eighty columns, which makes a line
        // someone is meant to copy unusable.
        usageHelpWidth = 96,
        usageHelpAutoWidth = true,
        description = "Analyse Oracle ADF applications and migrate them to Spring Boot.",
        // Help is the first thing most people read, and picocli's default reads like an
        // argument list. This lays out what the tool is for, in the order someone meets it.
        headerHeading = "%n",
        synopsisHeading = "%nUsage%n  ",
        descriptionHeading = "%n",
        commandListHeading = "%nCommands%n",
        optionListHeading = "%nOptions%n",
        // The footer is set in main() instead, because it differs between the two builds and
        // help that offers a command this jar does not have is worse than no help.
        footerHeading = "%n",
        subcommands = { AppsCommand.class, ScanCommand.class, AnalyzeCommand.class, ReportCommand.class,
                        StartCommand.class, GlossaryCommand.class })
public final class AdfMig implements Runnable {

    /** Reports the build this jar came from, rather than a number written out by hand. */
    static final class BuildVersion implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            var buffer = new java.io.ByteArrayOutputStream();
            Branding.banner(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
            return new String[] {
                    buffer.toString(java.nio.charset.StandardCharsets.UTF_8).stripTrailing(),
                    "  built " + com.adfmig.core.Version.built(),
                    "  " + Branding.copyright()
            };
        }
    }

    @CommandLine.Spec CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        // Someone who has just unzipped this and typed the name has not chosen a command yet.
        // At a terminal the walkthrough is a better answer than a page of usage.
        if (Terminal.isInteractive()) {
            System.exit(new CommandLine(new StartCommand()).execute());
        }
        // Through the spec, so this prints the footer main() built rather than a fresh copy of
        // the annotation's.
        spec.commandLine().usage(System.out);
    }

    /**
     * What to show someone reading help, which is not the same in the two builds. The free tool
     * must not offer 'generate' as though typing it would work, and the paid one must not
     * advertise itself to someone who has already bought it.
     */
    private static String[] footer(boolean pro) {
        var lines = new java.util.ArrayList<String>();
        lines.add("Getting started");
        lines.add("  adfmig                              walk through one migration");
        lines.add("  adfmig apps ~/adf                   what is in an estate, and what it would take");
        lines.add("  adfmig report ~/adf/Payments        assess one application");
        if (pro) {
            lines.add("  adfmig generate ~/adf/Payments      generate its replacement");
            lines.add("  adfmig license                      what is licensed, and to whom");
        }
        lines.add("");
        lines.add("Unfamiliar with ADF?");
        lines.add("  adfmig glossary                     what each ADF term becomes");
        lines.add("");
        if (pro) {
            lines.add("Assessment needs no licence. Generating a replacement does; 'adfmig license' shows");
            lines.add("what is installed.");
        } else {
            lines.add("Assessment is complete here and has no limits: any number of applications, no");
            lines.add("licence, no account. Generating the Spring Boot replacement is adfmig Pro.");
        }
        lines.add("");
        lines.add(Branding.COMPANY + "  ·  " + Branding.WEBSITE);
        lines.add("");
        return lines.toArray(String[]::new);
    }

    /** Exit code for work a licence refused, distinct from a genuine failure. */
    static final int NOT_LICENSED = 3;

    /**
     * Commands that exist only in the paid product. Named here so their absence can be explained
     * rather than reported as a malformed argument; nothing else about them is known here.
     */
    private static final java.util.Set<String> SOLD_SEPARATELY =
            java.util.Set.of("generate", "schema", "verify", "license");

    /**
     * What each command is actually used for, shown under its own help.
     *
     * <p>An option list says what every flag is called and nothing about which of them anyone
     * reaches for. These are the lines people copy.
     */
    private static final java.util.Map<String, String[]> EXAMPLES = java.util.Map.of(
            "apps", new String[] {
                    "Examples",
                    "  adfmig apps ~/adf                     how many applications, and of what kind",
                    "  adfmig apps ~/adf --list              every one of them, as a table",
                    "  adfmig apps ~/adf --filter payments   only those matching a name or path",
                    "  adfmig apps ~/adf --profile rest      only those already publishing REST",
                    "  adfmig apps ~/adf --json estate.json  the whole survey, for another tool"},
            "scan", new String[] {
                    "Examples",
                    "  adfmig scan ~/adf/Payments            what the application is made of",
                    "  adfmig scan ~/adf/Payments --show-ignored   including what does not migrate"},
            "analyze", new String[] {
                    "Examples",
                    "  adfmig analyze ~/adf/Payments         every endpoint, one row each",
                    "  adfmig analyze ~/adf/Payments --detail     bind variables, criteria, grants",
                    "  adfmig analyze ~/adf/Payments --sql   the query behind each endpoint",
                    "  adfmig analyze ~/adf --all            every application under the path"},
            "report", new String[] {
                    "Examples",
                    "  adfmig report ~/adf/Payments          assess one application",
                    "  adfmig report ~/adf --all             assess an estate, with a total",
                    "  adfmig report ~/adf/Payments -o out   write the HTML somewhere else",
                    "",
                    "The verdict is about whether the source says enough to generate working code,",
                    "which is a different question from how much work the migration is."},
            "schema", new String[] {
                    "Examples",
                    "  adfmig schema ~/adf/Payments --against prod-schema.sql",
                    "  adfmig schema ~/adf/Payments --against prod-schema.sql --show-unused",
                    "",
                    "Nothing connects to a database. --against takes the database's own DDL — a",
                    "schema export, or anything holding its CREATE TABLE statements — which is the",
                    "one thing a customer can send before an account has been agreed.",
                    "",
                    "This is the only check that can find an error in the ADF metadata itself.",
                    "Everything else is derived from that metadata, so a mistake in it agrees with",
                    "itself all the way down."},
            "start", new String[] {
                    "Examples",
                    "  adfmig start                          find, assess and migrate, step by step"},
            "glossary", new String[] {
                    "Examples",
                    "  adfmig glossary                       every ADF term, and what it becomes"});

    /**
     * Gives every command the same help as the top level.
     *
     * <p>Left alone, a subcommand prints a wrapped description and an option list, which reads
     * like a manual page for a tool nobody chose. Someone typing {@code --help} on one command has
     * already decided to use it and is asking how — so the headings match, and each one ends with
     * the invocations worth copying.
     */
    private static void styleSubcommandHelp(CommandLine commandLine) {
        commandLine.getSubcommands().forEach((name, subcommand) -> {
            var usage = subcommand.getCommandSpec().usageMessage();
            usage.headerHeading("%n");
            usage.synopsisHeading("%nUsage%n  ");
            usage.descriptionHeading("%n");
            usage.parameterListHeading("%nArguments%n");
            usage.optionListHeading("%nOptions%n");
            usage.footerHeading("%n");
            usage.footer(EXAMPLES.getOrDefault(name, new String[0]));
            subcommand.setUsageHelpWidth(96);
            subcommand.setUsageHelpAutoWidth(true);
        });
    }

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new AdfMig());

        // Whatever is installed adds its own commands. The free tool knows only that something
        // might, never what: the parts that are sold are not present in it to be found.
        var installed = ProExtension.find();
        installed.ifPresent(extension -> extension.commands().forEach(commandLine::addSubcommand));
        commandLine.getCommandSpec().usageMessage().footer(footer(installed.isPresent()));
        styleSubcommandHelp(commandLine);

        System.exit(commandLine
                // Someone typing a command that is sold rather than given away has not made a
                // mistake, and telling them their arguments are unmatched answers a question they
                // did not ask.
                .setParameterExceptionHandler((e, unmatched) -> {
                    String attempted = unmatched.length > 0 ? unmatched[0] : "";
                    // Only when the root command is the one refusing it. In the paid build these
                    // commands exist, so a bad option inside one raises from the subcommand — and
                    // answering that with "this is part of adfmig Pro" tells someone who already
                    // bought it that they have not.
                    boolean refusedByRoot = e.getCommandLine().getParent() == null;
                    if (refusedByRoot && SOLD_SEPARATELY.contains(attempted)) {
                        var err = e.getCommandLine().getErr();
                        err.println();
                        err.printf("  '%s' is part of adfmig Pro.%n", attempted);
                        err.println();
                        err.println("  This is the free assessment tool: it surveys an estate and reports what a");
                        err.println("  migration would take, with no limits. Generating the replacement, and the");
                        err.println("  tests that prove it matches the original, are the paid product.");
                        err.println();
                        err.println("  " + Branding.WEBSITE);
                        err.println();
                        return NOT_LICENSED;
                    }
                    // Anything else is a genuine mistake and gets picocli's own message.
                    var line = e.getCommandLine();
                    line.getErr().println(e.getMessage());
                    line.usage(line.getErr());
                    return CommandLine.ExitCode.USAGE;
                })
                // A refusal from the paid product is an answer, not a crash, and only that
                // product knows which of its failures are refusals. Asking it keeps the free
                // tool ignorant of licensing, which is the point of it being free.
                .setExecutionExceptionHandler((e, cmd, parseResult) -> ProExtension.find()
                        .flatMap(extension -> extension.explain(e, cmd.getErr()))
                        .orElseThrow(() -> e))
                .execute(args));
    }
}
