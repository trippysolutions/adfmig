package com.adfmig.cli;

import com.adfmig.core.Version;

import java.io.PrintStream;

/**
 * How the tool introduces itself.
 *
 * <p>Shown once, where someone has asked what this is — the walkthrough, the help, the version.
 * Not on every command: a banner printed before each of forty scans is noise, and it would end up
 * in the middle of anyone's build log.
 */
public final class Branding {

    public static final String COMPANY = "Trippy Solutions";
    public static final String WEBSITE = "trippysolutions.com";

    private Branding() {}

    /**
     * The mark. Deliberately small and drawn in characters that survive a font without box
     * drawing, a narrow window, and a terminal that is not UTF-8 — a logo that arrives broken
     * looks worse than no logo.
     */
    private static final String[] MARK = {
            "      _  __           _     ",
            "  ___| |/ _|_ __ ___ (_)__ _ ",
            " / _` | | |_| '_ ` _ \\| / _` |",
            "| (_| | |  _| | | | | | | (_| |",
            " \\__,_|_|_| |_| |_| |_|_|\\__, |",
            "                         |___/ ",
    };

    /** The full introduction, for the walkthrough and for --version. */
    public static void banner(PrintStream out) {
        out.println();
        for (String line : MARK) {
            out.println("  " + Terminal.cyan(line));
        }
        out.printf("  %s   %s%n",
                Terminal.bold("Oracle ADF to Spring Boot"),
                Terminal.dim(Version.describe()));
        out.printf("  %s%n", Terminal.dim(COMPANY + "  ·  " + WEBSITE));
        out.println();
    }

    /** One line, for places where the mark would be too much. */
    public static void line(PrintStream out) {
        out.printf("  %s  %s  %s%n",
                Terminal.bold("adfmig"),
                Terminal.dim(Version.current()),
                Terminal.dim(COMPANY + " · " + WEBSITE));
    }

    /**
     * The licence this build is actually under, which is not the same in both of them. The open
     * tool is MIT; the paid product is not. Rather than keep two strings in step by hand, ask
     * which build this is: the presence of the extension that generates <em>is</em> the
     * difference between them.
     */
    public static String copyright() {
        String owner = "Copyright (c) 2026 " + COMPANY + ". ";
        return owner + (ProExtension.find().isPresent()
                ? "Proprietary; see EULA.md."
                : "MIT licensed; see LICENSE.");
    }
}
