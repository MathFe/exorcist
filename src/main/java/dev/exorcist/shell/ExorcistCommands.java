package dev.exorcist.shell;

import dev.exorcist.ai.GhostService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class ExorcistCommands {

    private final GhostService ghostService;

    public ExorcistCommands(GhostService ghostService) {
        this.ghostService = ghostService;
    }

    @ShellMethod(key = "seance", value = "Check that the local model answers (talks to Ollama).", group = "Exorcist")
    public String seance(@ShellOption(defaultValue = "Is anyone there?", help = "What to ask the spirit") String message) {
        String answer = ghostService.seance(message);
        return "[" + ghostService.model() + "] " + answer;
    }
}
