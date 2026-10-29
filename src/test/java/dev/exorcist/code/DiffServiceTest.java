package dev.exorcist.code;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.exorcist.code.DiffService.Patch;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DiffServiceTest {

    private static final List<String> BEFORE = List.of(
            "class A {",
            "    void f() {",
            "        int x = 1;",
            "    }",
            "}");

    private final DiffService diffService = new DiffService(new CodeParserService());

    @Test
    void splicesTheReplacementAndRendersAUnifiedDiff() {
        Patch patch = diffService.replace("src/A.java", BEFORE, new LineRange(3, 3), List.of("        int x = 2;"));

        assertThat(patch.after()).containsExactly("class A {", "    void f() {", "        int x = 2;", "    }", "}");
        assertThat(patch.diff()).startsWith("--- a/src/A.java\n+++ b/src/A.java\n@@ -1,5 +1,5 @@\n")
                .contains("\n-        int x = 1;\n+        int x = 2;\n");
        assertThat(patch.stillParses()).isTrue();
        assertThat(patch.whitespaceOnly()).isFalse();
    }

    @Test
    void reindentsCodeTheModelPulledToTheLeft() {
        Patch patch = diffService.replace("A.java", BEFORE, new LineRange(2, 4),
                List.of("void f() {", "    int x = 2;", "", "}"));

        assertThat(patch.after()).containsExactly(
                "class A {", "    void f() {", "        int x = 2;", "", "    }", "}");
    }

    @Test
    void keepsCodeThatIsAlreadyIndentedEnough() {
        assertThat(DiffService.reindent(List.of("            deeper();"), List.of("    shallow();")))
                .containsExactly("            deeper();");
    }

    @Test
    void noticesAWhitespaceOnlyChange() {
        Patch patch = diffService.replace("A.java", BEFORE, new LineRange(3, 3), List.of("        int x  =  1;"));

        assertThat(patch.isEmpty()).isFalse();
        assertThat(patch.whitespaceOnly()).isTrue();
    }

    @Test
    void producesNoDiffWhenNothingChanges() {
        Patch patch = diffService.replace("A.java", BEFORE, new LineRange(3, 3), List.of("int x = 1;"));

        assertThat(patch.isEmpty()).isTrue();
    }

    @Test
    void noticesWhenTheResultNoLongerParses() {
        Patch patch = diffService.replace("A.java", BEFORE, new LineRange(3, 3), List.of("int x = ;"));

        assertThat(patch.stillParses()).isFalse();
    }

    @Test
    void theDiffAppliesWithGitApply(@TempDir Path dir) throws Exception {
        Patch patch = diffService.replace("A.java", BEFORE, new LineRange(2, 4),
                List.of("    int f() {", "        return 2;", "    }"));
        Files.writeString(dir.resolve("A.java"), String.join("\n", BEFORE) + "\n");

        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            git.apply().setPatch(new ByteArrayInputStream(patch.diff().getBytes(StandardCharsets.UTF_8))).call();
        }

        assertThat(Files.readAllLines(dir.resolve("A.java"))).isEqualTo(patch.after());
    }
}
