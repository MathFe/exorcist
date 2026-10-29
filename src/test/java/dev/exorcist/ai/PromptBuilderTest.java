package dev.exorcist.ai;

import java.util.Optional;

import dev.exorcist.ExorcistProperties;
import dev.exorcist.Fixtures;
import dev.exorcist.Possession;
import dev.exorcist.code.LineRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final Possession possession = Fixtures.possession();
    private final PromptBuilder builder = new PromptBuilder(new ExorcistProperties(500, 15, 80));

    @Test
    void theGhostIsTheAuthorWithTheMostCommits() {
        GhostPrompt prompt = builder.possess(possession);

        assertThat(prompt.system()).startsWith("You are the ghost of Alice, a Java developer who haunts the file "
                + Fixtures.PATH + ".");
        assertThat(prompt.user()).contains(
                "You are the spirit of: Alice, the author with the most commits on this file (2 of 3)",
                "File born: Wednesday 2001-10-31 at 23:30, by Alice, commit aaaaaaa \"Birth of the counter\"",
                "Last change by anyone: Sunday 2004-02-29 at 23:30, by Alice",
                "Authors: Alice (2 commits), Bob (1 commit)");
    }

    @Test
    void listsCommitMessagesOldestFirst() {
        String user = builder.possess(possession).user();

        assertThat(user).containsSubsequence(
                "- 2001-10-31 Alice: Birth of the counter",
                "- 2003-06-01 Bob: Catch everything",
                "- 2004-02-29 Alice: Count harder");
    }

    @Test
    void saysWhichLinesOtherHandsWrote() {
        assertThat(builder.possess(possession).user()).contains(
                "Lines alive at HEAD: 30. Last written by you: 26 (87%).",
                "Lines last written by other hands: lines 18-21 Bob (2003)");
    }

    @Test
    void numbersTheSourceAndSkipsTheLicense() {
        String user = builder.possess(possession).user();

        assertThat(user).contains(
                "=== SOURCE CODE (line numbers on the left; lines 1-3 are the license header and are omitted) ===",
                " 4| package org.example;",
                " 9| public class Counter extends Object implements Runnable {",
                "30| }");
        assertThat(user).doesNotContain("Licensed to the living");
    }

    @Test
    void outlinesTheClass() {
        assertThat(builder.possess(possession).user()).contains(
                "public class Counter extends Object implements Runnable (lines 8-30), package org.example",
                "- method public void run(): lines 16-22");
    }

    @Test
    void cutsLongFilesAndSaysSo() {
        PromptBuilder small = new PromptBuilder(new ExorcistProperties(5, 15, 2));

        String user = small.possess(possession).user();

        // Numbers are as wide as the last one shown: 4-8 needs one digit.
        assertThat(user).contains("8| /** Counts sheep. It never sleeps. */",
                "[lines 9-30 were cut to fit in memory; CODE STRUCTURE shows what they contain]",
                "- ... and 4 more members");
        assertThat(user).doesNotContain("9| public class");
    }

    @Test
    void slidesTheListingToShowTheLinesToExorcise() {
        PromptBuilder small = new PromptBuilder(new ExorcistProperties(5, 15, 80));
        ExorcismTarget target = ExorcismTarget.chosenByUser(new LineRange(26, 26));

        String user = small.exorcise(possession, Optional.empty(), Optional.of(target)).user();

        assertThat(user).contains("[lines 4-21 were cut", "26|         System.out.println(count);", "[lines 27-30 were cut");
    }

    @Test
    void confessionGetsTheScanAsEvidence() {
        assertThat(builder.confess(possession).user()).contains(
                "=== SUSPICIOUS SPOTS",
                "- lines 20-21: swallowed exception: empty catch block for Exception",
                "- line 26: console output: System.out.println() instead of a logger");
    }

    @Test
    void exorcismPinsTheLinesAndQuotesThem() {
        ExorcismTarget target = ExorcismTarget.choose(possession.parsed(), Optional.empty()).orElseThrow();

        String user = builder.exorcise(possession, Optional.of("1. (lines 20-21) I swallowed it."), Optional.of(target)).user();

        assertThat(user).contains(
                "=== YOUR CONFESSION (earlier in this seance) ===\n1. (lines 20-21) I swallowed it.",
                "=== THE CODE TO CAST OUT (lines 18-21, exactly as in the file) ===\n```java\n        try {",
                "The REPLACE LINES line must be exactly: REPLACE LINES 18-21");
    }

    @Test
    void exorcismWithoutATargetLetsTheGhostChoose() {
        String user = builder.exorcise(possession, Optional.empty(), Optional.empty()).user();

        assertThat(user).contains("Perform one exorcism: a refactoring of the worst sin in this class.")
                .doesNotContain("=== THE CODE TO CAST OUT", "must be exactly");
    }

    @Test
    void anEpitaphOnlyNeedsTheOutline() {
        GhostPrompt prompt = builder.epitaph(possession);

        assertThat(prompt.user()).contains("=== CODE STRUCTURE ===", "Its Javadoc says: Counts sheep.",
                "Born 2001-10-31, last changed 2004-02-29, haunted by Alice.");
        assertThat(prompt.user()).doesNotContain("SOURCE CODE", "GIT MEMORIES");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Here lies a counter.                     | Here lies a counter.
            "Here lies a counter."                   | Here lies a counter.
            Epitaph: **Here lies a counter.**        | Here lies a counter.
            """)
    void keepsOnlyTheEpitaph(String reply, String expected) {
        assertThat(PromptBuilder.epitaphLine(reply)).isEqualTo(expected);
    }

    @Test
    void keepsOnlyTheFirstLineOfTheEpitaph() {
        assertThat(PromptBuilder.epitaphLine("\n\n  Here lies a counter.\nI hope you like it.")).isEqualTo("Here lies a counter.");
    }

    @Test
    void shortensALongEpitaph() {
        String epitaph = PromptBuilder.epitaphLine("Boo ".repeat(40));

        assertThat(epitaph).hasSize(90).endsWith("...");
    }
}
