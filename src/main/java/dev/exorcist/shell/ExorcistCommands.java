package dev.exorcist.shell;

import java.nio.file.Path;

import dev.exorcist.ai.GhostService;
import dev.exorcist.git.GitHistoryService;
import dev.exorcist.git.Graveyard;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class ExorcistCommands {

    private final GhostService ghostService;
    private final GitHistoryService gitHistoryService;
    private final ExorcistSession session;
    private final Renderer renderer;

    public ExorcistCommands(GhostService ghostService, GitHistoryService gitHistoryService,
                            ExorcistSession session, Renderer renderer) {
        this.ghostService = ghostService;
        this.gitHistoryService = gitHistoryService;
        this.session = session;
        this.renderer = renderer;
    }

    @ShellMethod(key = "seance", value = "Check that the local model answers (talks to Ollama).", group = "Exorcist")
    public String seance(@ShellOption(defaultValue = "Is anyone there?", help = "What to ask the spirit") String message) {
        String answer = ghostService.seance(message);
        return "[" + ghostService.model() + "] " + answer;
    }

    @ShellMethod(key = "summon", value = "Scan a Git repository and list its Java classes with their authors.", group = "Exorcist")
    public String summon(
            @ShellOption(help = "Path to a Git repository, or a folder inside one") String path,
            @ShellOption(defaultValue = "50", help = "Maximum rows to show (0 = all)") int limit) {
        Graveyard graveyard = gitHistoryService.summon(Path.of(path));
        session.summoned(graveyard);
        return renderer.graveyard(graveyard, limit);
    }
}
