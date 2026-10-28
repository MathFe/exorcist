package dev.exorcist.git;

import java.nio.file.Path;
import java.util.List;

/**
 * The result of a summon: every Java file under the scanned directory, with its history.
 *
 * @param workTree root of the Git working tree
 * @param scope    the directory that was summoned (the work tree itself or a subdirectory of it)
 * @param ghosts   one profile per Java file present at HEAD
 * @param shallow  true for shallow clones, whose oldest commits are missing
 */
public record Graveyard(Path workTree, Path scope, List<GhostProfile> ghosts, boolean shallow) {

    public Graveyard {
        ghosts = List.copyOf(ghosts);
    }
}
