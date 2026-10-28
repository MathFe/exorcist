package dev.exorcist;

import dev.exorcist.code.ParsedClass;
import dev.exorcist.git.FileSnapshot;
import dev.exorcist.git.GhostProfile;

/** A class taken over by its ghost: its history, its source at HEAD and its parsed structure. */
public record Possession(GhostProfile ghost, FileSnapshot snapshot, ParsedClass parsed) {

    public String spirit() {
        return ghost.mainAuthor();
    }
}
