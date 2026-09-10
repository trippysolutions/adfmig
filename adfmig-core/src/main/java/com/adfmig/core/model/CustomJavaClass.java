package com.adfmig.core.model;

import java.util.List;

/**
 * A hand-written ADF Java class attached to a business component, and what is inside it.
 *
 * <p>Knowing a custom class exists is enough to warn about. It is not enough to price: an override
 * that applies a view criteria and returns a count is an afternoon, and one that walks a row set
 * issuing its own SQL is a week. Scoring both the same makes every estimate wrong in one direction
 * or the other.
 *
 * <p>Nothing here is executed or rewritten. The class is read to describe it.
 */
public record CustomJavaClass(
        String fqn,
        Kind kind,
        List<CustomMethod> methods,
        int lines,
        String sourcePath) {

    /** Which ADF base class this extends, which decides what its overrides mean. */
    public enum Kind { ENTITY, VIEW_OBJECT, VIEW_ROW, APPLICATION_MODULE, UNKNOWN }

    public String simpleName() {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    /** Methods a person has to rewrite, because nothing describes what they do. */
    public List<CustomMethod> needingRewrite() {
        return methods.stream().filter(m -> m.effort() == CustomMethod.Effort.MANUAL).toList();
    }

    public List<CustomMethod> translatable() {
        return methods.stream().filter(m -> m.effort() == CustomMethod.Effort.MECHANICAL).toList();
    }

    /** True when nothing in the class touches the ADF runtime, so it carries no ADF semantics. */
    public boolean isTrivial() {
        return methods.stream().noneMatch(m -> !m.adfApiCalls().isEmpty() || m.isLifecycleOverride());
    }
}
