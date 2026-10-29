package dev.exorcist.ai;

import java.util.Optional;

import dev.exorcist.Fixtures;
import dev.exorcist.code.CodeParserService;
import dev.exorcist.code.LineRange;
import dev.exorcist.code.ParsedClass;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExorcismTargetTest {

    private final ParsedClass counter = Fixtures.counter();

    @Test
    void withoutAConfessionPicksTheWorstSinTheScanFound() {
        ExorcismTarget target = ExorcismTarget.choose(counter, Optional.empty()).orElseThrow();

        // A swallowed exception outranks console output; the whole try statement is rewritten.
        assertThat(target.range()).isEqualTo(new LineRange(18, 21));
        assertThat(target.reason()).isEqualTo("the worst sin in this class, inside public void run(); "
                + "swallowed exception at lines 20-21: empty catch block for Exception");
    }

    @Test
    void prefersTheWorstSinTheGhostConfessed() {
        String confession = """
                1. (line 26) I printed to the console like an animal.
                2. (lines 20-21) I swallowed an exception.
                """;

        ExorcismTarget target = ExorcismTarget.choose(counter, Optional.of(confession)).orElseThrow();

        assertThat(target.range()).isEqualTo(new LineRange(26, 26));
        assertThat(target.reason()).startsWith("the worst sin you confessed, inside void log()");
    }

    @Test
    void ignoresConfessedSinsTheScanCannotConfirm() {
        ExorcismTarget target = ExorcismTarget.choose(counter, Optional.of("1. (line 13) An empty constructor.")).orElseThrow();

        assertThat(target.range()).isEqualTo(new LineRange(18, 21));
        assertThat(target.reason()).startsWith("the worst sin in this class");
    }

    @Test
    void skipsSinsTooLongToRewriteInOneAnswer() {
        String source = "class Long {\n    void sprawl() {\n"
                + "        int x = 0;\n".repeat(ExorcismTarget.MAX_LINES)
                + "        System.out.println(\"done\");\n    }\n}\n";
        ParsedClass parsed = new CodeParserService().parse(source, "Long");

        ExorcismTarget target = ExorcismTarget.choose(parsed, Optional.empty()).orElseThrow();

        int printLine = ExorcismTarget.MAX_LINES + 3;
        assertThat(target.range()).isEqualTo(new LineRange(printLine, printLine));
    }

    @Test
    void findsNothingInCleanCode() {
        ParsedClass clean = new CodeParserService().parse("class Clean {\n    private final int x = 1;\n}\n", "Clean");

        assertThat(ExorcismTarget.choose(clean, Optional.empty())).isEmpty();
    }
}
