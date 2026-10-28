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
}
