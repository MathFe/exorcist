package dev.exorcist.git;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Git remembers about one Java file.
 *
 * @param className  simple class name, taken from the file name
 * @param path       path relative to the repository root, with forward slashes
 * @param commits    every non-merge commit that touched the file, newest first
 */
public record GhostProfile(String className, String path, List<CommitInfo> commits) {

    public GhostProfile {
        if (commits.isEmpty()) {
            throw new IllegalArgumentException("A ghost needs at least one commit: " + path);
        }
        commits = List.copyOf(commits);
    }

    /** The commit that created the file. */
    public CommitInfo born() {
        return commits.getLast();
    }

    /** The last commit that touched the file. */
    public CommitInfo died() {
        return commits.getFirst();
    }

    /** Commits per author, most active first; ties go to whoever touched the file first. */
    public Map<String, Long> commitsByAuthor() {
        Map<String, Long> counts = new LinkedHashMap<>();
        commits.reversed().forEach(c -> counts.merge(c.author(), 1L, Long::sum));
        Map<String, Long> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** The author with the most commits on this file: the spirit that haunts it. */
    public String mainAuthor() {
        return commitsByAuthor().keySet().iterator().next();
    }

    /** Commits by the given author, newest first. */
    public List<CommitInfo> commitsBy(String author) {
        return commits.stream().filter(c -> c.author().equals(author)).toList();
    }

    /**
     * Best guess at the package from the file path, for display only (the parser reads the real one):
     * {@code src/main/java/org/junit/Assert.java} becomes {@code org.junit}.
     */
    public String packageHint() {
        String dir = "/" + path.substring(0, path.lastIndexOf('/') + 1);
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
}
