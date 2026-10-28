package dev.exorcist.shell;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                g -> repeatedNames.contains(g.className()) ? g.className() + " (" + packageHint(g.path()) + ")" : g.className()));

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

    /**
     * Best guess at the package from the file path, used only to tell apart classes with the same name:
     * {@code src/main/java/org/junit/Assert.java} becomes {@code org.junit}.
     */
    private static String packageHint(String path) {
        String dir = "/" + path.substring(0, Math.max(0, path.lastIndexOf('/'))) + "/";
        int sourceRoot = dir.lastIndexOf("/java/");
        String pkg;
        if (sourceRoot >= 0) {
            pkg = dir.substring(sourceRoot + "/java/".length());
        } else if (dir.startsWith("/src/")) {
            pkg = dir.substring("/src/".length());
        } else {
            pkg = dir.substring(1);
        }
        return pkg.isEmpty() ? "default package" : pkg.substring(0, pkg.length() - 1).replace('/', '.');
    }

    private static int width(List<String> values, String header, int max) {
        int widest = values.stream().mapToInt(String::length).max().orElse(0);
        return Math.min(max, Math.max(header.length(), widest));
    }

    private static String truncate(String value, int width) {
        return value.length() <= width ? value : value.substring(0, width - 3) + "...";
    }
}
