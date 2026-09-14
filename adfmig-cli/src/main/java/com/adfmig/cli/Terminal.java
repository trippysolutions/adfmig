package com.adfmig.cli;

import java.io.Console;
import java.io.PrintStream;

/**
 * Whether anyone is watching, and how to write for them.
 *
 * <p>The tool is used two ways: by a person at a terminal, who benefits from colour and from
 * knowing that something long is still moving, and by a script or a build, where the same output
 * is noise at best and corruption at worst. Spinner characters written into a redirected report
 * are a defect, not a flourish.
 *
 * <p>So everything here asks first. When output is piped, redirected, or the environment says not
 * to, it degrades to plain lines with no escape codes and no overwriting.
 */
public final class Terminal {

    private static final String ESC = "\033[";
    private static final boolean INTERACTIVE = detect();

    private Terminal() {}

    public static boolean isInteractive() {
        return INTERACTIVE;
    }

    private static boolean detect() {
        // An explicit request wins, so a build can force plain output without redirecting.
        if (Boolean.getBoolean("adfmig.plain") || System.getenv("ADFMIG_PLAIN") != null) return false;
        // https://no-color.org, respected because ignoring it is rude and costs nothing.
        if (System.getenv("NO_COLOR") != null) return false;

        Console console = System.console();
        if (console == null) return false;

        try {
            // Java 22 and later hand back a Console even when output is redirected, and this is
            // the only way to tell the difference. Older releases return null instead, so a
            // non-null Console already means a terminal.
            return (boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    // --- colour ------------------------------------------------------------------------

    public static String dim(String text)    { return wrap("2m", text); }
    public static String bold(String text)   { return wrap("1m", text); }
    public static String green(String text)  { return wrap("32m", text); }
    public static String yellow(String text) { return wrap("33m", text); }
    public static String red(String text)    { return wrap("31m", text); }
    public static String cyan(String text)   { return wrap("36m", text); }

    private static String wrap(String code, String text) {
        return INTERACTIVE ? ESC + code + text + ESC + "0m" : text;
    }

    // --- progress ----------------------------------------------------------------------

    /**
     * Reports that something long is still moving.
     *
     * <p>At a terminal it rewrites one line. Anywhere else it says what it is doing once, at the
     * start, and nothing further: a build log wants to know a step began, not to receive four
     * hundred updates about it.
     */
    public static final class Progress implements AutoCloseable {

        private static final char[] FRAMES = {'|', '/', '-', '\\'};

        private final PrintStream out;
        private final String what;
        private final long startedAt = System.nanoTime();
        private int frame;
        private int written;

        private Progress(PrintStream out, String what) {
            this.out = out;
            this.what = what;
            if (!INTERACTIVE) out.printf("  %s...%n", what);
        }

        public static Progress on(PrintStream out, String what) {
            return new Progress(out, what);
        }

        /**
         * Notes progress, and draws nothing.
         *
         * <p>This redrew one line in place, naming each application as it was read. On a real
         * terminal it did not stay on one line: it produced several hundred, stair-stepped across
         * the screen, burying the output the command was run for. Two attempts to fix the redraw
         * failed, both made from a machine with no terminal attached, where the code takes a
         * different path and the fault cannot be seen.
         *
         * <p>A survey takes a few seconds and ends by saying what it found. A spinner naming
         * files during it was never something anyone acted on, and losing the wanted output in
         * unwanted output is a worse failure than having no spinner.
         */
        public void update(String detail) {
            // Deliberately nothing. Kept so callers need not know whether anyone is watching.
        }

        /**
         * Keeps a progress line on one row.
         *
         * <p>A line longer than the window wraps, and then erasing it by writing its own length in
         * spaces wraps again and leaves the cursor somewhere else entirely — every update after
         * that lands further down the screen, and what should be one line redrawing becomes
         * hundreds of them. What it is reporting is never important enough to be worth that, so
         * the middle is dropped and the ends kept: the ends are what identifies it.
         */
        private static String fit(String line) {
            int width = columns();
            if (line.length() <= width) return line;
            int keep = (width - 3) / 2;
            return line.substring(0, keep) + "..." + line.substring(line.length() - keep);
        }

        /**
         * The window's width.
         *
         * <p>Java offers no way to ask, so this reads what the shell exports and falls back to
         * the narrowest window anyone still uses. Guessing too small costs a shortened line;
         * guessing too large costs the wrapping this exists to prevent.
         */
        private static int columns() {
            String declared = System.getenv("COLUMNS");
            if (declared != null) {
                try {
                    int value = Integer.parseInt(declared.trim());
                    if (value > 20) return value - 1;
                } catch (NumberFormatException ignored) {
                    // An unparseable COLUMNS is not worth a word to anyone.
                }
            }
            return 79;
        }

        /** Replaces the line with a final one, so the terminal is left showing the outcome. */
        public void done(String summary) {
            if (INTERACTIVE) {
                clear();
                out.printf("  %s %s  %s%n", green("done"), what, dim(summary + elapsed()));
            } else {
                out.printf("  %s: %s%s%n", what, summary, elapsed());
            }
        }

        private String elapsed() {
            long ms = (System.nanoTime() - startedAt) / 1_000_000;
            return ms < 1000 ? "" : String.format("  (%.1fs)", ms / 1000.0);
        }

        /**
         * Erases the line.
         *
         * <p>Asks the terminal to clear from the cursor to the end of the row rather than writing
         * that many spaces. Spaces have to be counted, and a count that is wrong by even one
         * character — a wide glyph, a line that wrapped — leaves debris or moves the cursor.
         */
        private void clear() {
            if (written > 0) {
                out.print("\r\u001b[K");
                written = 0;
            }
        }

        @Override
        public void close() {
            clear();
        }
    }
}
