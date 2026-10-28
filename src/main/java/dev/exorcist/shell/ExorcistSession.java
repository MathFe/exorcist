package dev.exorcist.shell;

import java.util.Optional;

import dev.exorcist.ExorcistException;
import dev.exorcist.Possession;
import dev.exorcist.git.Graveyard;
import org.springframework.stereotype.Component;

/** What the current shell session remembers between commands. One user, one shell: a singleton is enough. */
@Component
public class ExorcistSession {

    private Graveyard graveyard;
    private Possession possession;
    private String confession;

    public void summoned(Graveyard graveyard) {
        this.graveyard = graveyard;
        this.possession = null;
        this.confession = null;
    }

    public void possessed(Possession possession) {
        this.possession = possession;
        this.confession = null;
    }

    public void confessed(String confession) {
        this.confession = confession;
    }

    /** The possessed ghost's last confession, if it made one. */
    public Optional<String> confession() {
        return Optional.ofNullable(confession);
    }

    public Possession requirePossession() {
        if (possession == null) {
            throw new ExorcistException("No class is possessed yet. First: possess <ClassName>");
        }
        return possession;
    }

    public Optional<Graveyard> graveyard() {
        return Optional.ofNullable(graveyard);
    }

    public Graveyard requireGraveyard() {
        return graveyard().orElseThrow(() ->
                new ExorcistException("No repository has been summoned yet. Start with: summon <path>"));
    }
}
