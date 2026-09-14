package com.adfmig.cli;

import java.util.ArrayList;
import java.util.List;

/** Small text helpers shared by the commands. */
public final class Text {

    private Text() {}

    /**
     * Breaks a sentence onto lines no wider than {@code width}, at spaces only.
     *
     * <p>Explanations are written here as one string so they read as prose in the source. Someone
     * editing the wording should not also have to re-break the lines by hand, and a sentence that
     * has been hand-wrapped is a sentence nobody wants to edit.
     */
    public static List<String> wrapped(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
