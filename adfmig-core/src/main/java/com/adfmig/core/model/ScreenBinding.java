package com.adfmig.core.model;

import java.util.List;

/**
 * What one ADF Faces screen asked of the model.
 *
 * <p>A page definition looks like part of the user interface and is in fact a record of which
 * business operations a screen used: the view instance it read, the page size it read at, the
 * attributes it displayed and the methods it invoked.
 *
 * <p>That makes it the specification for the API a rebuilt front end needs. An ADF Faces
 * application publishes no REST at all, so there is no contract to preserve — but there is a
 * precise record of what the screens required, and an API built from it serves the same
 * information the application already showed.
 */
public record ScreenBinding(
        String id,
        List<Collection> collections,
        List<String> operations,
        String sourcePath) {

    /** True when the screen reads nothing itself, only composing other screens through task flows. */
    public boolean isEmpty() {
        return collections.isEmpty() && operations.isEmpty();
    }

    /**
     * One collection a screen displayed.
     *
     * @param viewUsage  the application module view instance it read
     * @param rangeSize  the page size the screen used
     * @param viewObject the view object behind that instance, as the page definition names it
     * @param attributes the attributes the screen actually displayed, which is usually a subset
     */
    public record Collection(
            String id,
            String viewUsage,
            String dataControl,
            Integer rangeSize,
            String viewObject,
            List<String> attributes) {}
}
