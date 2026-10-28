package dev.exorcist.shell;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

import dev.exorcist.ExorcistException;
import dev.exorcist.Possession;
import dev.exorcist.ai.GhostPrompt;
import dev.exorcist.ai.GhostService;
import dev.exorcist.ai.PromptBuilder;
import dev.exorcist.code.CodeParserService;
import dev.exorcist.code.ParsedClass;
import dev.exorcist.git.FileSnapshot;
import dev.exorcist.git.GhostProfile;
import dev.exorcist.git.GitHistoryService;
import dev.exorcist.git.Graveyard;
import org.jline.terminal.Terminal;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class ExorcistCommands {

    private final GhostService ghostService;
    private final GitHistoryService gitHistoryService;
    private final CodeParserService codeParserService;
    private final PromptBuilder promptBuilder;
    private final ExorcistSession session;
    private final Renderer renderer;
    private final Terminal terminal;

    public ExorcistCommands(GhostService ghostService, GitHistoryService gitHistoryService,
                            CodeParserService codeParserService, PromptBuilder promptBuilder,
                            ExorcistSession session, Renderer renderer, Terminal terminal) {
        this.ghostService = ghostService;
        this.gitHistoryService = gitHistoryService;
        this.codeParserService = codeParserService;
        this.promptBuilder = promptBuilder;
        this.session = session;
        this.renderer = renderer;
        this.terminal = terminal;
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

    @ShellMethod(key = "possess", value = "Let the ghost of a class's author explain it in first person.", group = "Exorcist")
    public void possess(@ShellOption(help = "Class name, qualified name or path, e.g. Assert or org.junit.Assert") String className) {
        Graveyard graveyard = session.requireGraveyard();
        GhostProfile ghost = resolve(graveyard, className);
        FileSnapshot snapshot = gitHistoryService.snapshot(graveyard, ghost);
        ParsedClass parsed = codeParserService.parse(snapshot.content(), ghost.className());
        Possession possession = new Possession(ghost, snapshot, parsed);
        session.possessed(possession);

        print(renderer.possessionHeader(possession, ghostService.model()));
        channel(promptBuilder.possess(possession));
        print("\n\nNext: confess | exorcise | epitaph\n");
    }

    private GhostProfile resolve(Graveyard graveyard, String className) {
        List<GhostProfile> matches = graveyard.find(className);
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.size() > 1) {
            throw new ExorcistException(renderer.candidates(className, matches));
        }
        if (gitHistoryService.findUntracked(graveyard, className).isPresent()) {
            throw new ExorcistException(className + " exists on disk but was never committed. "
                    + "No spirit haunts code without history.");
        }
        throw new ExorcistException("No spirit named " + className + " rests here. Run summon to see who does.");
    }

    /** Prints the ghost's answer as it is generated. */
    private void channel(GhostPrompt prompt) {
        PrintWriter out = terminal.writer();
        ghostService.stream(prompt)
                .doOnNext(chunk -> {
                    out.print(chunk);
                    out.flush();
                })
                .blockLast();
    }

    private void print(String text) {
        terminal.writer().print(text);
        terminal.writer().flush();
    }
}
