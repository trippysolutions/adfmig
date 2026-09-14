package com.adfmig.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The table's whole job is measuring what shows, so that is what these check. */
class TableTest {

    private static final String GREEN = "\033[32m";
    private static final String RESET = "\033[0m";

    @Test
    void countsOnlyWhatAppearsOnScreen() {
        assertThat(Table.visibleLength("plain")).isEqualTo(5);
        assertThat(Table.visibleLength(GREEN + "plain" + RESET)).isEqualTo(5);
        assertThat(Table.visibleLength("")).isZero();
    }

    @Test
    void survivesAnUnterminatedEscape() {
        assertThat(Table.visibleLength("\033[32")).isEqualTo(4);
    }

    @Test
    void keepsEveryRowTheSameWidthWhenOneCellIsColoured() {
        List<String> lines = render(Table.of("NAME", "STATE")
                .row("first", GREEN + "ready" + RESET)
                .row("second", "blocked"));

        assertThat(lines).extracting(Table::visibleLength).containsOnly(lines.get(0).length());
    }

    @Test
    void widensAColumnToItsLongestCell() {
        List<String> lines = render(Table.of("N").row("a-much-longer-cell"));

        assertThat(lines.get(3)).contains("a-much-longer-cell");
        assertThat(Table.visibleLength(lines.get(0))).isEqualTo("a-much-longer-cell".length() + 4);
    }

    @Test
    void dropsTheHeadOfAPathAndTheTailOfAnythingElse() {
        assertThat(render(Table.of("P").width(0, 12).tail(0).row("/very/long/path/Payments")).get(3))
                .contains(".../Payments");
        assertThat(render(Table.of("P").width(0, 10).row("abcdefghijklmnop")).get(3))
                .contains("abcdefg...");
    }

    @Test
    void leavesAColouredCellUncutRatherThanSeverItsEscape() {
        String coloured = GREEN + "a-very-long-coloured-value" + RESET;

        assertThat(render(Table.of("P").width(0, 8).row(coloured)).get(3)).contains(coloured);
    }

    @Test
    void putsRightAlignedCellsAgainstTheirRightEdge() {
        List<String> lines = render(Table.of("N").right(0).row(7).row(1234));

        assertThat(lines.get(3)).as("padded to the width of 1234").contains("|    7 |");
        assertThat(lines.get(4)).contains("| 1234 |");
    }

    @Test
    void alwaysDrawsTopHeaderSeparatorRowsAndBottom() {
        assertThat(render(Table.of("A").row("x"))).hasSize(5);
    }

    /** Renders with ASCII borders and no colour, which is what a non-terminal gets. */
    private static List<String> render(Table table) {
        var buffer = new ByteArrayOutputStream();
        var out = new PrintStream(buffer, true, StandardCharsets.US_ASCII);
        table.print(out, "");
        return List.of(buffer.toString(StandardCharsets.US_ASCII).split("\n"));
    }
}
