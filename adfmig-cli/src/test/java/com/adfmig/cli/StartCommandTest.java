package com.adfmig.cli;

import com.adfmig.core.estate.DiscoveredApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the parts of the walkthrough that decide what someone is shown and what they are told
 * when they get it wrong. Everything here is reachable without a terminal.
 */
class StartCommandTest {

    // --- the path prompt ---------------------------------------------------------------

    @Test
    void expandsALeadingTilde() {
        assertThat(StartCommand.resolve("~/adf").toString())
                .isEqualTo(System.getProperty("user.home") + "/adf");
    }

    @Test
    void offersTheNearestDirectoryWhenASegmentIsMistyped(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("corpus"));
        Path typo = home.resolve("corpu");

        assertThat(StartCommand.adviceFor(typo.toString(), typo))
                .anySatisfy(line -> assertThat(line).contains(home.resolve("corpus").toString()));
    }

    @Test
    void explainsThatALeadingSlashIsTheDiskRoot() {
        Path nowhere = Path.of("/no-such-place-at-all/nested");

        assertThat(StartCommand.adviceFor("/no-such-place-at-all/nested", nowhere))
                .anySatisfy(line -> assertThat(line).contains("~/"));
    }

    @Test
    void doesNotSuggestAnythingWhenNothingIsClose(@TempDir Path directory) throws Exception {
        Files.createDirectories(directory.resolve("completely-unrelated"));
        Path missing = directory.resolve("zzzz");

        assertThat(StartCommand.adviceFor(missing.toString(), missing)).isEmpty();
    }

    @Test
    void measuresASingleDroppedLetterAsOne() {
        assertThat(StartCommand.distance("corpu", "corpus")).isOne();
        assertThat(StartCommand.distance("CORPUS", "corpus")).isZero();
    }

    // --- choosing an application -------------------------------------------------------

    @Test
    void offersRestApplicationsBeforeAnythingElse() {
        List<DiscoveredApplication> shortlist = StartCommand.shortlist(List.of(
                faces("Faces", 4),
                library("Library", 40),
                rest("Rest", 1)));

        assertThat(shortlist).extracting(DiscoveredApplication::name)
                .containsExactly("Rest", "Faces", "Library");
    }

    @Test
    void putsTheLargestApplicationFirstWithinOneProfile() {
        assertThat(StartCommand.shortlist(List.of(faces("Small", 2), faces("Large", 30))))
                .extracting(DiscoveredApplication::name)
                .containsExactly("Large", "Small");
    }

    @Test
    void leavesOutApplicationsWithNothingToAssess() {
        assertThat(StartCommand.shortlist(List.of(empty("Empty"), faces("Real", 2))))
                .extracting(DiscoveredApplication::name)
                .containsExactly("Real");
    }

    @Test
    void fallsBackToEverythingWhenNoApplicationHasABusinessModel() {
        assertThat(StartCommand.shortlist(List.of(empty("Empty")))).hasSize(1);
    }

    @Test
    void searchesNameAndPathWithoutRegardToCase() {
        List<DiscoveredApplication> all = List.of(faces("Payments", 1), faces("Billing", 1));

        assertThat(StartCommand.search(all, "PAYMENT")).extracting(DiscoveredApplication::name)
                .containsExactly("Payments");
        assertThat(StartCommand.search(all, "nothing")).isEmpty();
    }

    private static DiscoveredApplication rest(String name, int entities) {
        return app(name, entities, 3, 0);
    }

    private static DiscoveredApplication faces(String name, int entities) {
        return app(name, entities, 0, 1);
    }

    private static DiscoveredApplication library(String name, int entities) {
        return app(name, entities, 0, 0);
    }

    private static DiscoveredApplication empty(String name) {
        return app(name, 0, 0, 0);
    }

    private static DiscoveredApplication app(String name, int entities, int restResources,
                                             int pageDefinitions) {
        return new DiscoveredApplication(name, "estate/" + name, name + ".jws",
                entities, 0, 0, restResources, pageDefinitions, 0, 0);
    }
}
