package com.adfmig.core;

import java.util.Properties;

/**
 * The build this jar came from.
 *
 * <p>Read from a resource Maven fills in, so the version exists in exactly one place. It was
 * previously written out by hand in four — the banner, the generated file headers, the report
 * footer and the generator defaults — which is three opportunities to ship output claiming to come
 * from a build it did not.
 *
 * <p>Every generated file carries it, so a customer asking why their output looks different from
 * the documentation can be answered without guessing.
 */
public final class Version {

    private static final Properties PROPERTIES = read();

    private Version() {}

    public static String current() {
        return PROPERTIES.getProperty("version", "unknown");
    }

    public static String built() {
        return PROPERTIES.getProperty("built", "unknown");
    }

    /** How the tool names itself on the console and in generated headers. */
    public static String describe() {
        return "adfmig " + current();
    }

    private static Properties read() {
        Properties properties = new Properties();
        try (var in = Version.class.getResourceAsStream("/adfmig-version.properties")) {
            if (in != null) properties.load(in);
        } catch (Exception ignored) {
            // A jar without its own version resource still runs; it just cannot name its build.
        }
        return properties;
    }
}
