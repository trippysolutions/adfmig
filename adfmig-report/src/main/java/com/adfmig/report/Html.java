package com.adfmig.report;

/** Escaping and small formatting helpers for report rendering. */
final class Html {

    private Html() {}

    /**
     * Escapes text for inclusion in HTML.
     *
     * <p>Every dynamic value in the report comes from customer source files — class names, SQL,
     * check constraints, file paths — none of which is trusted to be free of markup.
     */
    static String escape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Days, rounded to something an estimate can sensibly claim. */
    static String days(double value) {
        if (value < 10) return "%.1f".formatted(value);
        return "%.0f".formatted(value);
    }

    static String percent(double part, double whole) {
        if (whole <= 0) return "0";
        return "%.0f".formatted(part / whole * 100);
    }
}
