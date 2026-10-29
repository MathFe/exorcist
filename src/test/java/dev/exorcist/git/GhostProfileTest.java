package dev.exorcist.git;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static dev.exorcist.Fixtures.commit;
import static dev.exorcist.Fixtures.day;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GhostProfileTest {

    private final GhostProfile ghost = new GhostProfile("Assert", "src/main/java/org/junit/Assert.java", List.of(
            commit("d", "Bob", day(2010, 1, 4), "Tidy up"),
            commit("c", "Alice", day(2005, 3, 2), "Add assertEquals"),
            commit("b", "Bob", day(2003, 7, 9), "Add fail"),
            commit("a", "Alice", day(2001, 1, 1), "Initial import")));

    @Test
    void isBornWithTheOldestCommitAndDiesWithTheNewest() {
        assertThat(ghost.born().message()).isEqualTo("Initial import");
        assertThat(ghost.died().message()).isEqualTo("Tidy up");
    }

    @Test
    void isHauntedByTheMostActiveAuthorAndTiesGoToWhoeverCameFirst() {
        assertThat(ghost.commitsByAuthor()).containsExactly(
                Map.entry("Alice", 2L), Map.entry("Bob", 2L));
        assertThat(ghost.mainAuthor()).isEqualTo("Alice");
        assertThat(ghost.commitsBy("Bob")).extracting(CommitInfo::message).containsExactly("Tidy up", "Add fail");
    }

    @ParameterizedTest
    @CsvSource({
            "src/main/java/org/junit/Assert.java, org.junit",
            "modules/core/src/java/org/junit/Assert.java, org.junit",
            "src/org/junit/Assert.java, org.junit",
            "org/junit/Assert.java, org.junit",
            "Assert.java, default package",
            "src/main/java/Assert.java, default package"})
    void guessesThePackageFromThePath(String path, String expected) {
        GhostProfile profile = new GhostProfile("Assert", path, ghost.commits());

        assertThat(profile.packageHint()).isEqualTo(expected);
    }

    @Test
    void needsAtLeastOneCommit() {
        assertThatThrownBy(() -> new GhostProfile("Assert", "Assert.java", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shortensCommitIds() {
        assertThat(ghost.born().shortId()).isEqualTo("aaaaaaa");
    }
}
