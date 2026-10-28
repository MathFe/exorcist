package dev.exorcist.git;

import java.time.ZonedDateTime;

/** One commit that touched a file, as the ghost remembers it. */
public record CommitInfo(String id, String author, ZonedDateTime when, String message) {

    public String shortId() {
        return id.substring(0, 7);
    }

    CommitInfo withAuthor(String newAuthor) {
        return new CommitInfo(id, newAuthor, when, message);
    }
}
