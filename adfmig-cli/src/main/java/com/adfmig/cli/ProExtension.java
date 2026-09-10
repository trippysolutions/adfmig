package com.adfmig.cli;

import com.adfmig.core.model.AdfApplication;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * What the paid product adds, seen from the free one.
 *
 * <p>The free tool is open source, so it must have nothing to unlock: a licence check in code
 * anyone can edit is not a check, and a restriction anyone can delete is not a tier. The parts
 * that are sold are simply not present. This is the shape of the hole they fill.
 *
 * <p>Found through {@link ServiceLoader}, so the free tool works unchanged when nothing implements
 * it, and running the paid jar is the whole of the installation.
 */
public interface ProExtension {

    /** Commands to add, already annotated for the command line. */
    List<Object> commands();

    /** Whether a generator is present, so the walkthrough knows whether to offer one. */
    boolean canGenerate();

    /**
     * Generates the replacement.
     *
     * @return what was produced, or empty when this extension cannot generate
     */
    Optional<Summary> generate(AdfApplication application, Path outputRoot);

    /** One line describing what is installed, for the banner. */
    String describe();

    /**
     * Explains a failure this extension raised.
     *
     * <p>A refusal from the paid product is an answer rather than a crash, and only the paid
     * product knows which of its failures are refusals. The free tool asks rather than
     * recognising exception types it should know nothing about.
     *
     * @return the exit code to use, or empty if this is not an exception it recognises
     */
    default Optional<Integer> explain(Exception failure, java.io.PrintWriter err) {
        return Optional.empty();
    }

    /** What a generation produced, in terms the free tool can report without knowing more. */
    record Summary(String projectPath, String checklistPath, int filesWritten, int needingAttention) {}

    /** The installed extension, or empty when running the free tool. */
    static Optional<ProExtension> find() {
        return ServiceLoader.load(ProExtension.class).findFirst();
    }
}
