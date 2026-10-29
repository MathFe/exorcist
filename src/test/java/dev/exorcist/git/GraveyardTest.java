package dev.exorcist.git;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static dev.exorcist.Fixtures.commit;
import static dev.exorcist.Fixtures.day;
import static org.assertj.core.api.Assertions.assertThat;

class GraveyardTest {

    private final Graveyard graveyard = new Graveyard(Path.of("."), Path.of("."), List.of(
            ghost("src/main/java/org/junit/Assert.java"),
            ghost("src/main/java/junit/framework/Assert.java"),
            ghost("src/main/java/org/junit/runner/Runner.java"),
            ghost("src/main/java/org/junit/runner/RUNNER.java")),
            false, Map.of("kbeck", "Kent Beck"));

    @Test
    void findsAGhostBySimpleNameQualifiedNameOrPath() {
        assertThat(paths("Runner")).containsExactly("src/main/java/org/junit/runner/Runner.java");
        assertThat(paths("org.junit.Assert")).containsExactly("src/main/java/org/junit/Assert.java");
        assertThat(paths("org/junit/Assert.java")).containsExactly("src/main/java/org/junit/Assert.java");
        assertThat(paths("src\\main\\java\\org\\junit\\Assert.java")).containsExactly("src/main/java/org/junit/Assert.java");
    }

    @Test
    void returnsEveryGhostWhenTheNameIsAmbiguous() {
        assertThat(paths("Assert")).hasSize(2);
    }

    @Test
    void exactCaseWinsOverCaseInsensitiveMatches() {
        assertThat(paths("RUNNER")).containsExactly("src/main/java/org/junit/runner/RUNNER.java");
        assertThat(paths("org.junit.assert")).containsExactly("src/main/java/org/junit/Assert.java");
        assertThat(paths("runner")).hasSize(2);
    }

    @Test
    void findsNothingForAnUnknownName() {
        assertThat(graveyard.find("Sheep")).isEmpty();
    }

    @Test
    void mapsAliasesToTheirPerson() {
        assertThat(graveyard.canonicalAuthor("kbeck")).isEqualTo("Kent Beck");
        assertThat(graveyard.canonicalAuthor("Erich Gamma")).isEqualTo("Erich Gamma");
    }

    private List<String> paths(String query) {
        return graveyard.find(query).stream().map(GhostProfile::path).toList();
    }

    private static GhostProfile ghost(String path) {
        String className = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
        return new GhostProfile(className, path, List.of(commit("a", "Kent Beck", day(2001, 1, 1), "Import")));
    }
}
