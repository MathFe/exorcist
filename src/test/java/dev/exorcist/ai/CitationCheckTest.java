package dev.exorcist.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitationCheckTest {

    @Test
    void countsCitationsInEveryShape() {
        CitationCheck check = CitationCheck.of("The loop (lines 10-12) feeds line 14, see Lines 3 to 5 and lines 7–8.", 20);

        assertThat(check.citations()).isEqualTo(4);
        assertThat(check.outOfRange()).isEmpty();
        assertThat(check.clean()).isTrue();
    }

    @Test
    void flagsLinesThatDoNotExist() {
        CitationCheck check = CitationCheck.of("It starts at line 0, ends at lines 18-40 and again at line 40.", 20);

        assertThat(check.outOfRange()).containsExactly(0, 40);
        assertThat(check.clean()).isFalse();
    }

    @Test
    void flagsListItemsThatCiteNothing() {
        String confession = """
                1. (lines 3-5) A swallowed exception.
                2) A vague feeling of guilt.
                3. (line 9) Console output.
                """;

        CitationCheck check = CitationCheck.of(confession, 20);

        assertThat(check.citations()).isEqualTo(2);
        assertThat(check.uncited()).isEqualTo(1);
        assertThat(check.clean()).isFalse();
    }

    @Test
    void anAnswerWithoutCitationsIsNotClean() {
        assertThat(CitationCheck.of("Boo.", 20).clean()).isFalse();
    }

    @Test
    void listsCitedRangesInOrder() {
        assertThat(CitationCheck.citedRanges("First (lines 95-142), then (line 7), never lines 9-3."))
                .extracting(Object::toString)
                .containsExactly("lines 95-142", "line 7");
    }
}
