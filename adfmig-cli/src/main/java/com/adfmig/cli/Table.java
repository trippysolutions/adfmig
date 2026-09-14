package com.adfmig.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A bordered table that measures what shows rather than what is sent.
 *
 * <p>Columns aligned with {@code printf} break the moment a cell is coloured: the escape sequences
 * are characters to a format string and none of them are characters on screen, so every coloured
 * cell is short by the length of its own escapes and everything after it lands in the wrong place.
 * Every width here is computed from the visible text.
 *
 * <p>Borders are drawn with box-drawing characters where the output can carry them and with ASCII
 * where it cannot, so a redirected file or a terminal in a single-byte encoding gets a table rather
 * than a row of question marks.
 */
public final class Table {

    private static final String[] BOX   = {"┌", "┬", "┐", "├", "┼", "┤", "└", "┴", "┘", "─", "│"};
    private static final String[] ASCII = {"+", "+", "+", "+", "+", "+", "+", "+", "+", "-", "|"};

    private final List<String> headers;
    private final List<List<String>> rows = new ArrayList<>();
    private final Map<Integer, Integer> limits = new HashMap<>();
    private final Set<Integer> rightAligned = new HashSet<>();
    private final Set<Integer> keepTail = new HashSet<>();

    private Table(List<String> headers) {
        this.headers = headers;
    }

    public static Table of(String... headers) {
        return new Table(List.of(headers));
    }

    /** Caps a column's width; anything longer is truncated. */
    public Table width(int column, int max) {
        limits.put(column, max);
        return this;
    }

    /**
     * Truncates this column by dropping its beginning rather than its end. Paths are told apart by
     * their last few segments, so the head is the part worth losing.
     */
    public Table tail(int column) {
        keepTail.add(column);
        return this;
    }

    public Table right(int... columns) {
        for (int column : columns) rightAligned.add(column);
        return this;
    }

    public Table row(Object... cells) {
        List<String> row = new ArrayList<>(cells.length);
        for (Object cell : cells) row.add(cell == null ? "" : cell.toString());
        rows.add(row);
        return this;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public void print(PrintStream out, String indent) {
        String[] edge = borderFor(out);
        int[] widths = widths();

        out.println(indent + line(edge, widths, 0, 1, 2));
        out.println(indent + cells(edge, widths, headers, true));
        out.println(indent + line(edge, widths, 3, 4, 5));
        for (List<String> row : rows) out.println(indent + cells(edge, widths, row, false));
        out.println(indent + line(edge, widths, 6, 7, 8));
    }

    // --- layout ------------------------------------------------------------------------

    private int[] widths() {
        int[] widths = new int[headers.size()];
        for (int column = 0; column < headers.size(); column++) {
            widths[column] = visibleLength(headers.get(column));
        }
        for (List<String> row : rows) {
            for (int column = 0; column < widths.length && column < row.size(); column++) {
                widths[column] = Math.max(widths[column], visibleLength(cut(row.get(column), column)));
            }
        }
        return widths;
    }

    private String line(String[] edge, int[] widths, int left, int join, int right) {
        StringBuilder out = new StringBuilder(edge[left]);
        for (int column = 0; column < widths.length; column++) {
            out.append(edge[9].repeat(widths[column] + 2));
            out.append(column == widths.length - 1 ? edge[right] : edge[join]);
        }
        return out.toString();
    }

    private String cells(String[] edge, int[] widths, List<String> row, boolean header) {
        StringBuilder out = new StringBuilder(edge[10]);
        for (int column = 0; column < widths.length; column++) {
            String text = cut(column < row.size() ? row.get(column) : "", column);
            String shown = header ? Terminal.bold(text) : text;
            int padding = widths[column] - visibleLength(text);
            out.append(' ');
            if (rightAligned.contains(column)) out.append(" ".repeat(padding)).append(shown);
            else out.append(shown).append(" ".repeat(padding));
            out.append(' ').append(edge[10]);
        }
        return out.toString();
    }

    /**
     * Shortens a cell to its column's limit.
     *
     * <p>A cell carrying colour is left alone: cutting it would sever an escape sequence and spill
     * the colour across the rest of the row. Colour is only ever put on the last column, which is
     * the one nothing needs to be capped at.
     */
    private String cut(String text, int column) {
        Integer max = limits.get(column);
        if (max == null || text.length() <= max || text.indexOf('\033') >= 0) return text;
        return keepTail.contains(column)
                ? "..." + text.substring(text.length() - (max - 3))
                : text.substring(0, max - 3) + "...";
    }

    // --- what actually shows ------------------------------------------------------------

    /** The width a string occupies on screen, which excludes anything the terminal consumes. */
    static int visibleLength(String text) {
        int length = 0;
        for (int at = 0; at < text.length(); at++) {
            if (text.charAt(at) == '\033') {
                int end = text.indexOf('m', at);
                if (end < 0) return length + text.length() - at;
                at = end;
            } else {
                length++;
            }
        }
        return length;
    }

    /**
     * Box-drawing characters where the output stream can encode them, ASCII where it cannot.
     * Writing a character the encoding cannot carry produces a question mark, and a table drawn in
     * question marks is worse than one drawn in plus signs.
     */
    private static String[] borderFor(PrintStream out) {
        try {
            // The stream's own charset, not the platform's: the two differ whenever output has
            // been redirected, and the platform's is the one that gets this wrong.
            return out.charset().newEncoder().canEncode("─│┌") ? BOX : ASCII;
        } catch (RuntimeException cannotAsk) {
            return ASCII;
        }
    }
}
