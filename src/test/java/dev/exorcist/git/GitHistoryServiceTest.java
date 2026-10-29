package dev.exorcist.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import dev.exorcist.ExorcistException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs against real repositories, built commit by commit in a temporary directory. */
class GitHistoryServiceTest {

    private static final String OLD_PATH = "src/main/java/org/example/Counter.java";
    private static final String NEW_PATH = "src/main/java/org/example/sheep/SheepCounter.java";

    @TempDir
    Path dir;

    private Git git;
    private final GitHistoryService service = new GitHistoryService();

    @BeforeEach
    void initRepository() throws Exception {
        git = Git.init().setDirectory(dir.toFile()).setInitialBranch("main").call();
    }

    @AfterEach
    void closeRepository() {
        git.close();
    }

    /**
     * Alice creates Counter, Bob edits it, Alice moves it to SheepCounter, and "ali" (Alice from another
     * machine, same e-mail) edits it again. Bob also adds Fence, on a branch that Merger merges.
     */
    private void hauntedHistory() throws Exception {
        write(OLD_PATH, counter("class Counter", "count++;"));
        write("src/main/java/org/example/package-info.java", "package org.example;\n");
        write("README.md", "Sheep.\n");
        commit("Alice", "alice@example.com", 2001, "Birth of the counter");

        write(OLD_PATH, counter("class Counter", "count += 1;"));
        commit("Bob", "bob@example.com", 2003, "Count differently");

        Files.delete(dir.resolve(OLD_PATH));
        write(NEW_PATH, counter("class SheepCounter", "count += 1;"));
        commit("Alice", "alice@example.com", 2004, "Move to the sheep package");

        git.checkout().setCreateBranch(true).setName("fence").call();
        write("src/main/java/org/example/Fence.java", "package org.example;\n\nclass Fence {\n}\n");
        commit("Bob", "bob@example.com", 2005, "Build a fence");
        git.checkout().setName("main").call();

        write(NEW_PATH, counter("class SheepCounter", "count += 2;"));
        commit("ali", "Alice@Example.com", 2006, "Count two at a time");

        StoredConfig config = git.getRepository().getConfig();
        config.setString("user", null, "name", "Merger");
        config.setString("user", null, "email", "merger@example.com");
        git.merge().include(git.getRepository().resolve("fence")).setFastForward(MergeCommand.FastForwardMode.NO_FF).call();
    }

    @Test
    void listsEveryJavaClassAtHeadWithItsHistory() throws Exception {
        hauntedHistory();

        Graveyard graveyard = service.summon(dir);

        assertThat(graveyard.ghosts()).extracting(GhostProfile::path)
                .containsExactly("src/main/java/org/example/Fence.java", NEW_PATH);
        assertThat(graveyard.workTree()).isEqualTo(dir.toRealPath());
        assertThat(graveyard.shallow()).isFalse();
    }

    @Test
    void followsRenamesAndMergesAliases() throws Exception {
        hauntedHistory();

        GhostProfile ghost = service.summon(dir).find("SheepCounter").getFirst();

        assertThat(ghost.commits()).extracting(CommitInfo::message).containsExactly(
                "Count two at a time", "Move to the sheep package", "Count differently", "Birth of the counter");
        assertThat(ghost.commitsByAuthor()).containsExactly(Map.entry("Alice", 3L), Map.entry("Bob", 1L));
        assertThat(ghost.born().when().getYear()).isEqualTo(2001);
    }

    @Test
    void ignoresMergeCommits() throws Exception {
        hauntedHistory();

        GhostProfile fence = service.summon(dir).find("Fence").getFirst();

        assertThat(fence.commits()).extracting(CommitInfo::author).containsExactly("Bob");
    }

    @Test
    void summonsOnlyTheFolderAskedFor() throws Exception {
        hauntedHistory();

        Graveyard graveyard = service.summon(dir.resolve("src/main/java/org/example/sheep"));

        assertThat(graveyard.ghosts()).extracting(GhostProfile::className).containsExactly("SheepCounter");
        assertThat(graveyard.workTree()).isEqualTo(dir.toRealPath());
    }

    @Test
    void readsTheFileAtHeadWithItsBlame() throws Exception {
        hauntedHistory();
        Graveyard graveyard = service.summon(dir);
        GhostProfile ghost = graveyard.find("SheepCounter").getFirst();
        write(NEW_PATH, "uncommitted changes are not the ghost's\n");

        FileSnapshot snapshot = service.snapshot(graveyard, ghost);

        assertThat(snapshot.content()).isEqualTo(counter("class SheepCounter", "count += 2;"));
        assertThat(snapshot.blame()).hasSize(snapshot.content().lines().toList().size());
        // Line 7 was last written by "ali", shown under the name Alice used most; line 3 by the rename.
        assertThat(snapshot.blame().get(6).author()).isEqualTo("Alice");
        assertThat(snapshot.blame().get(6).when().getYear()).isEqualTo(2006);
        assertThat(snapshot.blame().get(2).when().getYear()).isEqualTo(2004);
    }

    @Test
    void decodesLatin1Files() throws Exception {
        Files.write(dir.resolve("Cafe.java"), "// Café\nclass Cafe {\n}\n".getBytes(StandardCharsets.ISO_8859_1));
        commit("Alice", "alice@example.com", 2001, "Coffee");
        Graveyard graveyard = service.summon(dir);

        FileSnapshot snapshot = service.snapshot(graveyard, graveyard.ghosts().getFirst());

        assertThat(snapshot.content()).startsWith("// Café");
    }

    @Test
    void findsJavaFilesThatWereNeverCommitted() throws Exception {
        hauntedHistory();
        write("src/main/java/org/example/Ghostless.java", "class Ghostless {\n}\n");
        write("target/classes/Compiled.java", "class Compiled {\n}\n");
        Graveyard graveyard = service.summon(dir);

        assertThat(service.findUntracked(graveyard, "org.example.Ghostless")).isPresent();
        assertThat(service.findUntracked(graveyard, "Compiled")).isEmpty();
    }

    @Test
    void refusesAFolderThatDoesNotExist() {
        assertThatThrownBy(() -> service.summon(dir.resolve("nowhere")))
                .isInstanceOf(ExorcistException.class)
                .hasMessageContaining("There is no crypt");
    }

    @Test
    void refusesARepositoryWithoutCommits() {
        assertThatThrownBy(() -> service.summon(dir))
                .isInstanceOf(ExorcistException.class)
                .hasMessageContaining("no commits yet");
    }

    @Test
    void refusesARepositoryWithoutJava() throws Exception {
        write("README.md", "Nothing to see.\n");
        commit("Alice", "alice@example.com", 2001, "Docs only");

        assertThatThrownBy(() -> service.summon(dir))
                .isInstanceOf(ExorcistException.class)
                .hasMessageContaining("No Java spirits");
    }

    @Test
    void refusesAFolderOutsideGit(@TempDir Path elsewhere) {
        // Only meaningful when the temp folder itself is not inside some Git repository.
        assumeTrue(new FileRepositoryBuilder().findGitDir(elsewhere.toFile()).getGitDir() == null);

        assertThatThrownBy(() -> service.summon(elsewhere))
                .isInstanceOf(ExorcistException.class)
                .hasMessageContaining("No Git history haunts");
    }

    private static String counter(String header, String statement) {
        return "package org.example;\n\n" + header + " {\n    int count;\n\n    void tick() {\n        "
                + statement + "\n    }\n}\n";
    }

    private void write(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void commit(String name, String email, int year, String message) throws Exception {
        PersonIdent author = new PersonIdent(name, email,
                ZonedDateTime.of(year, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        git.add().addFilepattern(".").call();
        git.add().addFilepattern(".").setUpdate(true).call();
        git.commit().setAuthor(author).setCommitter(author).setMessage(message).call();
    }
}
