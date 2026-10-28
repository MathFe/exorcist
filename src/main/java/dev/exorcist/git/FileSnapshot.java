package dev.exorcist.git;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * A file as it is at HEAD, with who last wrote each of its lines ({@code git blame}).
 *
 * @param content the file contents at HEAD
 * @param blame   one entry per line, in order: {@code blame.get(0)} is line 1
 */
public record FileSnapshot(String content, List<LineBlame> blame) {

    public FileSnapshot {
        blame = List.copyOf(blame);
    }

    /** Who last changed a line, and when. */
    public record LineBlame(String author, ZonedDateTime when) {
    }
}
