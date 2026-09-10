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

        /** Updates the line. Cheap enough to call often; it only redraws at a terminal. */
        public void update(String detail) {
            if (!INTERACTIVE) return;
            String line = String.format("  %c %s  %s", FRAMES[frame++ % FRAMES.length], what, detail);
            clear();
            out.print(line);
            out.flush();
            written = line.length();
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

        private void clear() {
            if (written > 0) {
                out.print("\r" + " ".repeat(written) + "\r");
                written = 0;
            }
        }

        @Override
        public void close() {
            clear();
        }
    }
}
