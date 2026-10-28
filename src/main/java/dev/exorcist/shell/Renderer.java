package dev.exorcist.shell;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import dev.exorcist.Possession;
import dev.exorcist.ai.CitationCheck;
import dev.exorcist.code.DiffService.Patch;
import dev.exorcist.code.LineRange;
import dev.exorcist.code.ParsedClass;
import dev.exorcist.git.GhostProfile;
import dev.exorcist.git.Graveyard;
import org.springframework.stereotype.Component;

/** Turns results into coloured text for the terminal. Keeps formatting out of the commands. */
@Component
public class Renderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_CLASS_WIDTH = 56;
    private static final int MAX_AUTHOR_WIDTH = 24;
    private static final int EPITAPH_WIDTH = 34;
    private static final int MIN_STONE_WIDTH = 38;

    private final Style style;

    public Renderer(Style style) {
        this.style = style;
    }

    /** Printed when the interactive shell starts. */
    public String banner(String model) {
        return String.join("\n",
                style.bone("     .-."),
                style.bone("    (o o)   ") + style.title("E X O R C I S T"),
                style.bone("    | O \\   ") + style.voice("summon the ghosts of legacy Java code"),
                style.bone("     \\   \\  ") + style.dim("model: " + model + ", running locally via Ollama"),
                style.bone("      `~~~'"),
                "",
                style.dim("Start with: summon <path-to-a-git-repo>   |   help"),
                "");
    }

    /** Lists the ghosts, longest dead first, showing at most {@code limit} rows (0 = all). */
    public String graveyard(Graveyard graveyard, int limit) {
        List<GhostProfile> ghosts = graveyard.ghosts().stream()
                .sorted(Comparator.comparing((GhostProfile g) -> g.died().when()))
                .toList();
        List<GhostProfile> shown = limit > 0 && limit < ghosts.size() ? ghosts.subList(0, limit) : ghosts;
        Set<String> repeatedNames = ghosts.stream()
                .collect(Collectors.groupingBy(GhostProfile::className, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Map<GhostProfile, String> labels = shown.stream().collect(Collectors.toMap(Function.identity(),
                g -> repeatedNames.contains(g.className()) ? g.className() + " (" + g.packageHint() + ")" : g.className()));

        int classWidth = width(List.copyOf(labels.values()), "Class", MAX_CLASS_WIDTH);
        int authorWidth = width(shown.stream().map(GhostProfile::mainAuthor).toList(), "Spirit", MAX_AUTHOR_WIDTH);

        StringBuilder out = new StringBuilder();
        out.append("%s Java spirits rest in %s, longest dead first.%n%n"
                .formatted(style.title(String.valueOf(ghosts.size())), style.dim(graveyard.scope().toString())));
        out.append(style.title(row(classWidth, authorWidth, "Class", "Spirit", "Born", "Died", "Commits"))).append('\n');
        out.append(style.dim(row(classWidth, authorWidth, "-".repeat(classWidth), "-".repeat(authorWidth),
                "-".repeat(10), "-".repeat(10), "-".repeat(7)))).append('\n');
        for (GhostProfile ghost : shown) {
            // Pad first, colour after: escape codes would throw off the column widths.
            out.append(pad(truncate(labels.get(ghost), classWidth), classWidth)).append("  ")
                    .append(style.spirit(pad(truncate(ghost.mainAuthor(), authorWidth), authorWidth))).append("  ")
                    .append(style.dim(DATE.format(ghost.born().when()))).append("  ")
                    .append(style.dim(DATE.format(ghost.died().when()))).append("  ")
                    .append("%7d".formatted(ghost.commits().size())).append('\n');
        }
        if (shown.size() < ghosts.size()) {
            out.append(style.dim("%n... and %d more restless spirits (use --limit 0 to see them all).%n"
                    .formatted(ghosts.size() - shown.size())));
        }
        if (graveyard.shallow()) {
            out.append('\n').append(style.warning("This is a shallow clone: the oldest memories are missing. "
                    + "Run 'git fetch --unshallow' for the full story.")).append('\n');
        }
        out.append('\n').append(style.dim("Next: possess <ClassName>"));
        return out.toString();
    }

    /** Shown before the ghost starts talking. */
    public String possessionHeader(Possession possession, String model) {
        GhostProfile ghost = possession.ghost();
        ParsedClass parsed = possession.parsed();
        StringBuilder header = new StringBuilder()
                .append(style.title("Possessing " + parsed.qualifiedName())).append(' ')
                .append(style.dim("(" + ghost.path() + ")")).append('\n')
                .append("Spirit: ").append(style.spirit(possession.spirit()))
                .append(style.dim(" | born %s | died %s | %d commits | %d lines".formatted(
                        DATE.format(ghost.born().when()), DATE.format(ghost.died().when()),
                        ghost.commits().size(), parsed.lines().size())))
                .append('\n');
        if (!parsed.parsed()) {
            header.append(style.warning("The parser could not read this file; the ghost only sees raw lines.")).append('\n');
        }
        return header.append(style.dim("The air grows cold... (" + model + ")")).append("\n\n").toString();
    }

    /** Tells the user how well the ghost kept the "no line number, no claim" rule. */
    public String citationReport(CitationCheck check) {
        if (check.clean()) {
            return "\n\n" + style.success("[%d line citations, all inside the file]".formatted(check.citations())) + "\n";
        }
        List<String> problems = new ArrayList<>();
        if (check.citations() == 0) {
            problems.add("the ghost cited no lines at all, so treat its claims with suspicion");
        }
        if (!check.outOfRange().isEmpty()) {
            problems.add("it cited lines that do not exist: " + check.outOfRange().stream()
                    .map(String::valueOf).collect(Collectors.joining(", ")));
        }
        if (check.uncited() > 0) {
            problems.add(check.uncited() + (check.uncited() == 1 ? " item cites" : " items cite") + " no line");
        }
        return "\n\n" + style.warning("[Warning: " + String.join("; ", problems) + "]") + "\n";
    }

    public String exorcismTarget(String target) {
        return style.dim("Target: ") + target + "\n" + style.dim("The ritual begins...") + "\n\n";
    }

    /** The proposed refactoring, as a diff ready for {@code git apply}. */
    public String exorcism(Patch patch, LineRange range) {
        if (patch.isEmpty() || patch.whitespaceOnly()) {
            return "\n\n" + style.warning(("The ritual failed: the ghost only rewrote %s as they were, and the sin remains."
                    + " Try exorcise again, or aim at fewer lines with --lines.").formatted(range)) + "\n";
        }
        StringBuilder out = new StringBuilder("\n\n").append(style.title("The exorcism of " + range)).append("\n\n");
        patch.diff().lines().forEach(line -> out.append(style.diffLine(line)).append('\n'));
        if (!patch.stillParses()) {
            out.append('\n').append(style.warning("[Warning: the file no longer parses with this change. "
                    + "Treat it as a sketch, not a cure.]")).append('\n');
        }
        return out.append('\n')
                .append(style.dim("Nothing was written to disk. To apply it, save the diff to a file and run: git apply <file>"))
                .append('\n').toString();
    }

    /**
     * A tombstone for the possessed class:
     * <pre>
     *         ______________________________
     *       .'                              '.
     *      /                                  \
     *     |             R . I . P              |
     *     |           BaseTestRunner           |
     * ~~~~|____________________________________|~~~~
     * </pre>
     */
    public String epitaph(Possession possession, String epitaph) {
        GhostProfile ghost = possession.ghost();
        int mourners = ghost.commitsByAuthor().size() - 1;
        List<StoneLine> carving = new ArrayList<>();
        carving.add(new StoneLine("R . I . P", style::bone));
        carving.add(StoneLine.BLANK);
        carving.add(new StoneLine(possession.parsed().name(), style::title));
        carving.add(new StoneLine(ghost.packageHint(), style::dim));
        carving.add(StoneLine.BLANK);
        carving.add(new StoneLine("haunted by", style::dim));
        carving.add(new StoneLine(possession.spirit(), style::spirit));
        carving.add(StoneLine.BLANK);
        carving.add(new StoneLine(DATE.format(ghost.born().when()) + "  -  " + DATE.format(ghost.died().when()), style::bone));
        carving.add(StoneLine.BLANK);
        wrap("\"" + epitaph + "\"", EPITAPH_WIDTH).forEach(line -> carving.add(new StoneLine(line, style::voice)));
        carving.add(StoneLine.BLANK);
        carving.add(new StoneLine("%d commits | %d %s".formatted(ghost.commits().size(), mourners,
                mourners == 1 ? "mourner" : "mourners"), style::dim));

        int inner = Math.max(MIN_STONE_WIDTH, carving.stream().mapToInt(l -> l.text().length()).max().orElse(0) + 6);
        String indent = "    ";
        StringBuilder out = new StringBuilder("\n");
        out.append(style.bone(indent + "    " + "_".repeat(inner - 6))).append('\n');
        out.append(style.bone(indent + "  .'" + " ".repeat(inner - 6) + "'.")).append('\n');
        out.append(style.bone(indent + " /" + " ".repeat(inner - 2) + "\\")).append('\n');
        for (StoneLine line : carving) {
            out.append(style.bone(indent + "|")).append(line.paint().apply(center(line.text(), inner)))
                    .append(style.bone("|")).append('\n');
        }
        out.append(style.dim("~~~~")).append(style.bone("|" + "_".repeat(inner) + "|")).append(style.dim("~~~~")).append('\n');
        return out.toString();
    }

    public String error(String message) {
        return style.error(message);
    }

    /** Lists the classes a name could refer to, when it is ambiguous. */
    public String candidates(String query, List<GhostProfile> matches) {
        return "Several spirits answer to '%s'. Be more specific:%n%s".formatted(query, matches.stream()
                .map(g -> "  possess " + (g.packageHint().equals("default package")
                        ? g.path()
                        : g.packageHint() + "." + g.className() + "   (" + g.path() + ")"))
                .collect(Collectors.joining(System.lineSeparator())));
    }

    /** One line carved in the stone, with its colour. */
    private record StoneLine(String text, UnaryOperator<String> paint) {
        static final StoneLine BLANK = new StoneLine("", UnaryOperator.identity());
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String center(String text, int width) {
        int left = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, left)) + text + " ".repeat(Math.max(0, width - left - text.length()));
    }

    private static String row(int classWidth, int authorWidth, String c, String a, String born, String died, String commits) {
        return pad(c, classWidth) + "  " + pad(a, authorWidth) + "  " + pad(born, 10) + "  " + pad(died, 10) + "  " + "%7s".formatted(commits);
    }

    private static String pad(String value, int width) {
        return value + " ".repeat(Math.max(0, width - value.length()));
    }

    private static int width(List<String> values, String header, int max) {
        int widest = values.stream().mapToInt(String::length).max().orElse(0);
        return Math.min(max, Math.max(header.length(), widest));
    }

    private static String truncate(String value, int width) {
        return value.length() <= width ? value : value.substring(0, width - 3) + "...";
    }
}
