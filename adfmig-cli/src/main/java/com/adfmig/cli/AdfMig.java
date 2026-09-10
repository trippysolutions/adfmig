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
        footerHeading = "%n",
        footer = {
                "Getting started",
                "  adfmig                              walk through one migration",
                "  adfmig apps ~/adf                   what is in an estate, and what it would take",
                "  adfmig report ~/adf/Payments        assess one application",
                "  adfmig generate ~/adf/Payments      generate its replacement",
                "",
                "Unfamiliar with ADF?",
                "  adfmig glossary                     what each ADF term becomes",
                "",
                "Without a licence adfmig surveys an estate and assesses one application, which is",
                "enough to decide whether a migration is worth doing. See 'adfmig license'.",
                "",
                "Trippy Solutions  ·  trippysolutions.com",
                ""
        },
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

    @Override
    public void run() {
        // Someone who has just unzipped this and typed the name has not chosen a command yet.
        // At a terminal the walkthrough is a better answer than a page of usage.
        if (Terminal.isInteractive()) {
            System.exit(new CommandLine(new StartCommand()).execute());
        }
        CommandLine.usage(this, System.out);
    }

    /** Exit code for work a licence refused, distinct from a genuine failure. */
    static final int NOT_LICENSED = 3;

    /**
     * Commands that exist only in the paid product. Named here so their absence can be explained
     * rather than reported as a malformed argument; nothing else about them is known here.
     */
    private static final java.util.Set<String> SOLD_SEPARATELY =
            java.util.Set.of("generate", "license");

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new AdfMig());

        // Whatever is installed adds its own commands. The free tool knows only that something
        // might, never what: the parts that are sold are not present in it to be found.
        ProExtension.find().ifPresent(extension ->
                extension.commands().forEach(commandLine::addSubcommand));

        System.exit(commandLine
                // Someone typing a command that is sold rather than given away has not made a
                // mistake, and telling them their arguments are unmatched answers a question they
                // did not ask.
                .setParameterExceptionHandler((e, unmatched) -> {
                    String attempted = unmatched.length > 0 ? unmatched[0] : "";
                    if (SOLD_SEPARATELY.contains(attempted)) {
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
