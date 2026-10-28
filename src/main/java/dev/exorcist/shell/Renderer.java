package dev.exorcist.shell;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.exorcist.Possession;
import dev.exorcist.ai.CitationCheck;
import dev.exorcist.code.DiffService.Patch;
import dev.exorcist.code.LineRange;
import dev.exorcist.code.ParsedClass;
import dev.exorcist.git.GhostProfile;
import dev.exorcist.git.Graveyard;
import org.springframework.stereotype.Component;

/** Turns results into text for the terminal. Keeps formatting out of the commands. */
@Component
public class Renderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_CLASS_WIDTH = 56;
    private static final int MAX_AUTHOR_WIDTH = 24;

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
        String row = "%-" + classWidth + "s  %-" + authorWidth + "s  %-10s  %-10s  %7s%n";

        StringBuilder out = new StringBuilder();
        out.append("%d Java spirits rest in %s, longest dead first.%n%n".formatted(ghosts.size(), graveyard.scope()));
        out.append(row.formatted("Class", "Spirit", "Born", "Died", "Commits"));
        out.append(row.formatted("-".repeat(classWidth), "-".repeat(authorWidth), "-".repeat(10), "-".repeat(10), "-".repeat(7)));
        for (GhostProfile ghost : shown) {
            out.append(row.formatted(
                    truncate(labels.get(ghost), classWidth),
                    truncate(ghost.mainAuthor(), authorWidth),
                    DATE.format(ghost.born().when()),
                    DATE.format(ghost.died().when()),
                    ghost.commits().size()));
        }
        if (shown.size() < ghosts.size()) {
            out.append("%n... and %d more restless spirits (use --limit 0 to see them all).%n"
                    .formatted(ghosts.size() - shown.size()));
        }
        if (graveyard.shallow()) {
            out.append("%nThis is a shallow clone: the oldest memories are missing. Run 'git fetch --unshallow' for the full story.%n"
                    .formatted());
        }
        out.append("%nNext: possess <ClassName>".formatted());
        return out.toString();
    }

    /** Shown before the ghost starts talking. */
    public String possessionHeader(Possession possession, String model) {
        GhostProfile ghost = possession.ghost();
        ParsedClass parsed = possession.parsed();
        String header = """
                Possessing %s (%s)
                Spirit: %s | born %s | died %s | %d commits | %d lines
                """.formatted(parsed.qualifiedName(), ghost.path(), possession.spirit(),
                DATE.format(ghost.born().when()), DATE.format(ghost.died().when()),
                ghost.commits().size(), parsed.lines().size());
        if (!parsed.parsed()) {
            header += "The parser could not read this file; the ghost only sees raw lines.\n";
        }
        return header + "The air grows cold... (" + model + ")\n\n";
    }

    /** Tells the user how well the ghost kept the "no line number, no claim" rule. */
    public String citationReport(CitationCheck check) {
        if (check.clean()) {
            return "\n\n[%d line citations, all inside the file]\n".formatted(check.citations());
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
        return "\n\n[Warning: " + String.join("; ", problems) + "]\n";
    }

    /** The proposed refactoring, as a diff ready for {@code git apply}. */
    public String exorcism(Patch patch, LineRange range) {
        if (patch.isEmpty() || patch.whitespaceOnly()) {
            return ("\n\nThe ritual failed: the ghost only rewrote %s as they were, and the sin remains."
                    + " Try exorcise again, or aim at fewer lines with --lines.\n").formatted(range);
        }
        StringBuilder out = new StringBuilder("\n\nThe exorcism of ").append(range).append(":\n\n").append(patch.diff());
        if (!patch.stillParses()) {
            out.append("\n[Warning: the file no longer parses with this change. Treat it as a sketch, not a cure.]\n");
        }
        return out.append("\nNothing was written to disk. To apply it, save the diff to a file and run: git apply <file>\n")
                .toString();
    }

    /** Lists the classes a name could refer to, when it is ambiguous. */
    public String candidates(String query, List<GhostProfile> matches) {
        return "Several spirits answer to '%s'. Be more specific:%n%s".formatted(query, matches.stream()
                .map(g -> "  possess " + (g.packageHint().equals("default package")
                        ? g.path()
                        : g.packageHint() + "." + g.className() + "   (" + g.path() + ")"))
                .collect(Collectors.joining(System.lineSeparator())));
    }

    private static int width(List<String> values, String header, int max) {
        int widest = values.stream().mapToInt(String::length).max().orElse(0);
        return Math.min(max, Math.max(header.length(), widest));
    }

    private static String truncate(String value, int width) {
        return value.length() <= width ? value : value.substring(0, width - 3) + "...";
    }
}
