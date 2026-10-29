package dev.exorcist.ai;

import dev.exorcist.code.LineRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExorcismReplyTest {

    @Test
    void readsTheExplanationTheRangeAndTheCode() {
        String reply = """
                I swallowed that exception (lines 18-21). No more.
                REPLACE LINES 18-21
                ```java
                try {
                    count++;
                } catch (IllegalStateException e) {
                    throw e;
                }
                ```
                """;

        ExorcismReply exorcism = ExorcismReply.parse(reply).orElseThrow();

        assertThat(exorcism.explanation()).isEqualTo("I swallowed that exception (lines 18-21). No more.");
        assertThat(exorcism.range()).isEqualTo(new LineRange(18, 21));
        assertThat(exorcism.code()).containsExactly(
                "try {", "    count++;", "} catch (IllegalStateException e) {", "    throw e;", "}");
    }

    @Test
    void acceptsLooseVariantsOfTheReplaceLine() {
        assertThat(ExorcismReply.parse("Replace lines: 7 – 9\n```\nint x;\n```").orElseThrow().range())
                .isEqualTo(new LineRange(7, 9));
        assertThat(ExorcismReply.parse("REPLACE LINE 7\n```java\nint x;\n```").orElseThrow().range())
                .isEqualTo(new LineRange(7, 7));
    }

    @Test
    void takesTheCodeAfterTheReplaceLineWhenTheFencesAreMissing() {
        ExorcismReply exorcism = ExorcismReply.parse("Begone.\nREPLACE LINES 3-3\nlogger.info(\"x\");\n").orElseThrow();

        assertThat(exorcism.code()).containsExactly("logger.info(\"x\");");
    }

    @Test
    void rejectsRepliesThatDoNotFollowTheFormat() {
        assertThat(ExorcismReply.parse("I refuse to leave this file.")).isEmpty();
        assertThat(ExorcismReply.parse("REPLACE LINES 9-3\n```java\nint x;\n```")).isEmpty();
        assertThat(ExorcismReply.parse("REPLACE LINES 3-9\n```java\n\n```")).isEmpty();
    }
}
