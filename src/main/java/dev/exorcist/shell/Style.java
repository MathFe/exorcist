package dev.exorcist.shell;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.stereotype.Component;

/**
 * The colour palette. Text is painted through JLine, which only emits the escape codes the terminal
 * supports, so output stays plain when redirected to a file; setting {@code NO_COLOR} turns colours off.
 */
@Component
public class Style {

    private static final int PURPLE = 141;
    private static final int LAVENDER = 183;
    private static final int PUMPKIN = 208;
    private static final int BONE = 250;
    private static final int GRAVE = 244;

    private final Terminal terminal;
    private final boolean enabled = System.getenv("NO_COLOR") == null;

    public Style(Terminal terminal) {
        this.terminal = terminal;
    }

    /** Headings and class names. */
    public String title(String text) {
        return paint(text, AttributedStyle.BOLD.foreground(PUMPKIN));
    }

    /** Author names: the spirits. */
    public String spirit(String text) {
        return paint(text, AttributedStyle.DEFAULT.foreground(PURPLE));
    }

    /** What the ghost says. */
    public String voice(String text) {
        return paint(text, AttributedStyle.DEFAULT.foreground(LAVENDER));
    }

    /** Stone, for the tombstone. */
    public String bone(String text) {
        return paint(text, AttributedStyle.DEFAULT.foreground(BONE));
    }

    /** Secondary information: dates, paths, hints. */
    public String dim(String text) {
        return paint(text, AttributedStyle.DEFAULT.foreground(GRAVE));
    }

    public String success(String text) {
        return paint(text, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
    }

    public String warning(String text) {
        return paint(text, AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
    }

    public String error(String text) {
        return paint(text, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
    }

    /** Colours one line of a unified diff by its kind. */
    public String diffLine(String line) {
        if (line.startsWith("+++") || line.startsWith("---")) {
            return paint(line, AttributedStyle.BOLD);
        }
        if (line.startsWith("@@")) {
            return paint(line, AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
        }
        if (line.startsWith("+")) {
            return success(line);
        }
        if (line.startsWith("-")) {
            return error(line);
        }
        return line;
    }

    /** The shell prompt, which JLine renders itself. */
    public AttributedString prompt(String text) {
        return new AttributedString(text, enabled ? AttributedStyle.BOLD.foreground(PURPLE) : AttributedStyle.DEFAULT);
    }

    private String paint(String text, AttributedStyle style) {
        if (!enabled || text.isEmpty()) {
            return text;
        }
        return new AttributedString(text, style).toAnsi(terminal);
    }
}
