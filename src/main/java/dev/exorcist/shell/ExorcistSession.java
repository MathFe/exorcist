package dev.exorcist.shell;

import java.util.Optional;

import dev.exorcist.ExorcistException;
import dev.exorcist.git.Graveyard;
import org.springframework.stereotype.Component;

/** What the current shell session remembers between commands. One user, one shell: a singleton is enough. */
@Component
public class ExorcistSession {

    private Graveyard graveyard;

    public void summoned(Graveyard graveyard) {
        this.graveyard = graveyard;
    }

    public Optional<Graveyard> graveyard() {
        return Optional.ofNullable(graveyard);
    }

    public Graveyard requireGraveyard() {
        return graveyard().orElseThrow(() ->
                new ExorcistException("No repository has been summoned yet. Start with: summon <path>"));
    }
}
