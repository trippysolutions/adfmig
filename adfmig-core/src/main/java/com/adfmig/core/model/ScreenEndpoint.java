package com.adfmig.core.model;

import java.util.List;
import java.util.Locale;

/**
 * An endpoint proposed for an ADF Faces application, derived from what its screens read.
 *
 * <p>Unlike an {@link Endpoint}, this URL is <strong>new</strong>. The original application
 * published nothing over HTTP, so nothing is being preserved and nothing can be compared against
 * it — the generated contract tests do not cover these. What can be said is that the collection
 * behind it is one the application already showed, at the page size it showed it.
 *
 * @param usedByScreens the page definitions that read this collection, so a team rebuilding a
 *                      screen can find the endpoint it needs
 */
public record ScreenEndpoint(
        String viewUsage,
        ApplicationModule module,
        ViewObject viewObject,
        EntityObject entityObject,
        Integer rangeSize,
        List<String> usedByScreens) {

    /** ADF's own default when a screen declares no range size. */
    private static final int DEFAULT_RANGE_SIZE = 25;

    public int pageSize() {
        return rangeSize == null || rangeSize <= 0 ? DEFAULT_RANGE_SIZE : rangeSize;
    }

    /**
     * The URL. Named after the view instance the screens read rather than after a screen, so the
     * API describes the data rather than today's page layout.
     */
    public String url() {
        String name = viewUsage.replaceAll("\\d+$", "");
        return "/api/" + Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public String simpleName() {
        String name = viewUsage.replaceAll("\\d+$", "");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** True when the collection can be written as well as read. */
    public boolean isWritable() {
        return entityObject != null && !entityObject.primaryKey().isEmpty();
    }

    @Override
    public String toString() {
        return url() + " (" + viewObject.simpleName() + ", read by "
                + String.join(", ", usedByScreens) + ")".toLowerCase(Locale.ROOT);
    }
}
