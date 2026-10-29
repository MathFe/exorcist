package dev.exorcist.shell;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeHidingPrinterTest {

    private final StringWriter out = new StringWriter();
    private final CodeHidingPrinter printer = new CodeHidingPrinter(new PrintWriter(out), text -> text);

    @Test
    void showsTheTextAndOneDotPerLineOfCode() {
        printer.accept("Begone, sin.\nREPLACE LINES 1-2\n```java\nint a;\nint b;\n```\n");

        assertThat(out).hasToString("Begone, sin.\nREPLACE LINES 1-2\n[the ghost writes the cure ...]\n");
    }

    @Test
    void handlesFencesSplitAcrossChunks() {
        String reply = "Begone.\n```java\nint a;\n```";
        for (String chunk : new String[] {"Begone.\n`", "`", "`java\nint", " a;\n``", "`"}) {
            printer.accept(chunk);
        }

        assertThat(out).hasToString("Begone.\n[the ghost writes the cure ..]");
        assertThat(printer.finish()).isEqualTo(reply);
    }

    @Test
    void printsInlineBackticksAsTheyAre() {
        printer.accept("Use `List` instead of `Vector`");
        printer.finish();

        assertThat(out).hasToString("Use `List` instead of `Vector`");
    }

    @Test
    void finishFlushesTextHeldBack() {
        printer.accept("A dangling ``");
        assertThat(out).hasToString("A dangling ");

        printer.finish();
        assertThat(out).hasToString("A dangling ``");
    }
}
