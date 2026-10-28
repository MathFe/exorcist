package dev.exorcist.code;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.exorcist.ExorcistException;

/** An inclusive range of 1-based line numbers. */
public record LineRange(int begin, int end) {

    private static final Pattern FORMAT = Pattern.compile("\\s*(\\d+)\\s*(?:-\\s*(\\d+)\\s*)?");

    public LineRange {
        if (begin < 1 || end < begin) {
            throw new IllegalArgumentException("Invalid line range: " + begin + "-" + end);
        }
    }

    /** Parses {@code "40-52"} or {@code "40"}. */
    public static LineRange parse(String text) {
        Matcher m = FORMAT.matcher(text);
        if (!m.matches()) {
            throw new ExorcistException("'" + text + "' is not a line range. Use the form 40-52.");
        }
        int begin = Integer.parseInt(m.group(1));
        int end = m.group(2) == null ? begin : Integer.parseInt(m.group(2));
        if (begin < 1 || end < begin) {
            throw new ExorcistException("'" + text + "' is not a valid line range: the first line must be 1 or more and not after the last.");
        }
        return new LineRange(begin, end);
    }

    public int size() {
        return end - begin + 1;
    }

    @Override
    public String toString() {
        return begin == end ? "line " + begin : "lines " + begin + "-" + end;
    }
}
