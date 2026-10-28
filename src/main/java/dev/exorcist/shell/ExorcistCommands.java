package dev.exorcist.shell;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import dev.exorcist.ExorcistException;
import dev.exorcist.Possession;
import dev.exorcist.ai.CitationCheck;
import dev.exorcist.ai.ExorcismReply;
import dev.exorcist.ai.ExorcismTarget;
import dev.exorcist.ai.GhostPrompt;
import dev.exorcist.ai.GhostService;
import dev.exorcist.ai.PromptBuilder;
import dev.exorcist.code.CodeParserService;
import dev.exorcist.code.DiffService;
import dev.exorcist.code.DiffService.Patch;
import dev.exorcist.code.LineRange;
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
    private final DiffService diffService;
    private final Renderer renderer;
    private final Style style;
    private final Terminal terminal;

    public ExorcistCommands(GhostService ghostService, GitHistoryService gitHistoryService,
                            CodeParserService codeParserService, PromptBuilder promptBuilder,
                            DiffService diffService, ExorcistSession session, Renderer renderer, Style style,
                            Terminal terminal) {
        this.ghostService = ghostService;
        this.gitHistoryService = gitHistoryService;
        this.codeParserService = codeParserService;
        this.promptBuilder = promptBuilder;
        this.diffService = diffService;
        this.session = session;
        this.renderer = renderer;
        this.style = style;
        this.terminal = terminal;
    }

    @ShellMethod(key = "seance", value = "Check that the local model answers (talks to Ollama).", group = "Exorcist")
    public String seance(@ShellOption(defaultValue = "Is anyone there?", help = "What to ask the spirit") String message) {
        String answer = ghostService.seance(message);
        return style.dim("[" + ghostService.model() + "] ") + style.voice(answer);
    }

    @ShellMethod(key = "summon", value = "Scan a Git repository and list its Java classes with their authors.", group = "Exorcist")
    public String summon(
            @ShellOption(help = "Path to a Git repository, or a folder inside one") String path,
            @ShellOption(defaultValue = "50", help = "Maximum rows to show (0 = all)") int limit) {
        print(style.dim("Opening the crypt and reading its history...") + "\n");
        Graveyard graveyard = gitHistoryService.summon(Path.of(path));
        session.summoned(graveyard);
        return renderer.graveyard(graveyard, limit);
    }

    @ShellMethod(key = "possess", value = "Let the ghost of a class's author explain it in first person.", group = "Exorcist")
    public void possess(@ShellOption(help = "Class name, qualified name or path, e.g. Assert or org.junit.Assert") String className) {
        Possession possession = possessionOf(className);
        session.possessed(possession);

        print(renderer.possessionHeader(possession, ghostService.model()));
        String explanation = channel(promptBuilder.possess(possession));
        print(renderer.citationReport(CitationCheck.of(explanation, possession.parsed().lines().size())));
        print("\n" + style.dim("Next: confess | exorcise | epitaph") + "\n");
    }

    @ShellMethod(key = "confess", value = "Make the possessing ghost confess the class's problems, line by line.", group = "Exorcist")
    public void confess() {
        Possession possession = session.requirePossession();
        print(style.dim("The spirit of ") + style.spirit(possession.spirit()) + style.dim(" kneels before ")
                + style.title(possession.parsed().name()) + style.dim("...") + "\n\n");
        String confession = channel(promptBuilder.confess(possession));
        session.confessed(confession);
        print(renderer.citationReport(CitationCheck.of(confession, possession.parsed().lines().size())));
        print("\n" + style.dim("Next: exorcise (to cast out the worst sin) | epitaph") + "\n");
    }

    @ShellMethod(key = "epitaph", value = "Carve a tombstone for the possessed class (or the one named).", group = "Exorcist")
    public String epitaph(@ShellOption(defaultValue = ShellOption.NULL,
            help = "Class to bury (default: the possessed one)") String className) {
        Possession possession = className == null ? session.requirePossession() : possessionOf(className);
        print(style.dim("Carving the stone...") + "\n");
        String fallback = possession.parsed().summary().isEmpty()
                ? "Here lies a class that did its best."
                : possession.parsed().summary();
        try {
            String carved = PromptBuilder.epitaphLine(ghostService.ask(promptBuilder.epitaph(possession)));
            return renderer.epitaph(possession, carved.isEmpty() ? fallback : carved);
        } catch (ExorcistException e) {
            // The stone can be carved without the model: fall back to the class's own Javadoc.
            return renderer.epitaph(possession, fallback) + "\n" + style.warning(e.getMessage());
        }
    }

    @ShellMethod(key = "exorcise", value = "Show a suggested refactoring as a unified diff. Never changes files.", group = "Exorcist")
    public void exorcise(@ShellOption(defaultValue = ShellOption.NULL,
            help = "Lines to refactor, e.g. 40-52 (default: the ghost's worst sin)") String lines) {
        Possession possession = session.requirePossession();
        List<String> source = possession.parsed().lines();
        Optional<ExorcismTarget> target = lines == null
                ? ExorcismTarget.choose(possession.parsed(), session.confession())
                : Optional.of(ExorcismTarget.chosenByUser(LineRange.parse(lines)));
        target.map(ExorcismTarget::range).filter(range -> range.end() > source.size()).ifPresent(range -> {
            throw new ExorcistException("%s has only %d lines; %s do not exist.".formatted(
                    possession.parsed().name(), source.size(), range));
        });

        print(renderer.exorcismTarget(target.map(t -> t.range() + ", " + t.reason()).orElse("the ghost chooses its own")));
        CodeHidingPrinter printer = new CodeHidingPrinter(terminal.writer(), style::voice);
        ghostService.stream(promptBuilder.exorcise(possession, session.confession(), target))
                .doOnNext(printer::accept)
                .blockLast();
        String reply = printer.finish();

        ExorcismReply exorcism = ExorcismReply.parse(reply).orElseThrow(() -> new ExorcistException(
                "\n\nThe ritual failed: the ghost did not answer with REPLACE LINES and a code block. Try exorcise again."));
        LineRange range = target.map(ExorcismTarget::range).orElse(exorcism.range());
        if (range.end() > source.size()) {
            throw new ExorcistException("\n\nThe ritual failed: the ghost tried to replace %s, beyond the end of the file. Try again."
                    .formatted(range));
        }
        Patch patch = diffService.replace(possession.ghost().path(), source, range, exorcism.code());
        print(renderer.exorcism(patch, range));
    }

    /** Finds the class, reads it at HEAD with its blame, and parses it. */
    private Possession possessionOf(String className) {
        Graveyard graveyard = session.requireGraveyard();
        GhostProfile ghost = resolve(graveyard, className);
        FileSnapshot snapshot = gitHistoryService.snapshot(graveyard, ghost);
        ParsedClass parsed = codeParserService.parse(snapshot.content(), ghost.className());
        return new Possession(ghost, snapshot, parsed);
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

    /** Prints the ghost's answer as it is generated, and returns all of it. */
    private String channel(GhostPrompt prompt) {
        PrintWriter out = terminal.writer();
        StringBuilder answer = new StringBuilder();
        ghostService.stream(prompt)
                .doOnNext(chunk -> {
                    answer.append(chunk);
                    out.print(style.voice(chunk));
                    out.flush();
                })
                .blockLast();
        return answer.toString();
    }

    private void print(String text) {
        terminal.writer().print(text);
        terminal.writer().flush();
    }
}
