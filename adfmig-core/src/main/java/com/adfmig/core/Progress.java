package com.adfmig.core;

/**
 * Somewhere to report that a long piece of work is still moving.
 *
 * <p>Deliberately one method and no dependencies. The parser should not know whether anyone is
 * watching, whether the output is a terminal, or what a spinner is; it should only be able to say
 * what it is doing. Everything about how that is shown belongs to whatever passes one in.
 */
@FunctionalInterface
public interface Progress {

    /** Ignores everything, for callers that have nothing to show it on. */
    Progress SILENT = detail -> { };

    void report(String detail);
}
