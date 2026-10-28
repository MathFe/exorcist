package dev.exorcist.shell;

import java.io.PrintWriter;
import java.util.function.UnaryOperator;

/**
 * Prints a streamed answer as it arrives, but shows code blocks only as a row of dots (one per line of code).
 * Used by {@code exorcise}, whose code is shown afterwards as a diff, so it is not printed twice.
 */
final class CodeHidingPrinter {

    private static final String FENCE = "```";

    private final PrintWriter out;
    private final UnaryOperator<String> paint;
    private final StringBuilder text = new StringBuilder();
    private int printed;
    private boolean inCode;

    CodeHidingPrinter(PrintWriter out, UnaryOperator<String> paint) {
        this.out = out;
        this.paint = paint;
    }

    void accept(String chunk) {
        text.append(chunk);
        StringBuilder visible = new StringBuilder();
        while (printed < text.length()) {
            char c = text.charAt(printed);
            if (c == '`') {
                int available = Math.min(FENCE.length(), text.length() - printed);
                String ahead = text.substring(printed, printed + available);
                if (available < FENCE.length() && FENCE.startsWith(ahead)) {
                    break; // could be the start of a fence split across chunks: wait for more text
                }
                if (ahead.equals(FENCE)) {
                    inCode = !inCode;
                    printed += FENCE.length();
                    visible.append(inCode ? "[the ghost writes the cure " : "]");
                    continue;
                }
            }
            if (!inCode) {
                visible.append(c);
            } else if (c == '\n') {
                visible.append('.');
            }
            printed++;
        }
        out.print(paint.apply(visible.toString()));
        out.flush();
    }

    /** Prints anything held back and returns the whole answer. */
    String finish() {
        if (!inCode && printed < text.length()) {
            out.print(paint.apply(text.substring(printed)));
        }
        out.flush();
        return text.toString();
    }
}
