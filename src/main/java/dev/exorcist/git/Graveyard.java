package dev.exorcist.git;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * The result of a summon: every Java file under the scanned directory, with its history.
 *
 * @param workTree      root of the Git working tree
 * @param scope         the directory that was summoned (the work tree itself or a subdirectory of it)
 * @param ghosts        one profile per Java file present at HEAD
 * @param shallow       true for shallow clones, whose oldest commits are missing
 * @param authorAliases every author name seen in the history, mapped to the name shown for that person
 */
public record Graveyard(Path workTree, Path scope, List<GhostProfile> ghosts, boolean shallow,
                        Map<String, String> authorAliases) {

    public Graveyard {
        ghosts = List.copyOf(ghosts);
        authorAliases = Map.copyOf(authorAliases);
    }

    public String canonicalAuthor(String name) {
        return authorAliases.getOrDefault(name, name);
    }

    /**
     * Finds ghosts by simple class name ({@code Assert}), qualified name ({@code org.junit.Assert})
     * or path ({@code src/main/java/org/junit/Assert.java}). Exact case wins over case-insensitive matches.
     */
    public List<GhostProfile> find(String query) {
        String name = query.strip().replace('\\', '/');
        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - ".java".length());
        }
        List<GhostProfile> exact = matching(name, String::equals, String::endsWith);
        return exact.isEmpty()
                ? matching(name, String::equalsIgnoreCase, (path, suffix) -> path.toLowerCase().endsWith(suffix.toLowerCase()))
                : exact;
    }

    private List<GhostProfile> matching(String name, BiPredicate<String, String> sameName,
                                        BiPredicate<String, String> pathEndsWith) {
        String pathSuffix = (name.contains("/") ? name : name.replace('.', '/')) + ".java";
        return ghosts.stream()
                .filter(g -> sameName.test(g.className(), name)
                        || sameName.test(g.path(), pathSuffix)
                        || pathEndsWith.test(g.path(), "/" + pathSuffix))
                .toList();
    }
}
