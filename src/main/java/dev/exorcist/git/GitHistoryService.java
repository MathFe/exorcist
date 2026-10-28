package dev.exorcist.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.exorcist.ExorcistException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

/**
 * Reads the Git history of a repository and builds a {@link GhostProfile} for each Java file.
 *
 * <p>History is read in a single walk from HEAD back to the root, instead of one {@code git log}
 * per file, so summoning a repository with thousands of commits stays fast. Renames are followed:
 * commits made before a file was moved still count towards it.
 */
@Service
public class GitHistoryService {

    private static final String JAVA_SUFFIX = ".java";
    private static final Set<String> NON_CLASS_FILES = Set.of("package-info", "module-info");

    public Graveyard summon(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new ExorcistException("There is no crypt at " + directory.toAbsolutePath()
                    + ". Check the path and try again.");
        }
        try {
            return summonFrom(directory.toRealPath());
        } catch (IOException e) {
            throw new ExorcistException("The crypt's records crumbled while reading Git history: " + e.getMessage(), e);
        }
    }

    private Graveyard summonFrom(Path scope) throws IOException {
        try (Repository repository = open(scope)) {
            Path workTree = repository.getWorkTree().toPath().toRealPath();
            String prefix = relativePrefix(workTree, scope);
            ObjectId head = repository.resolve("HEAD^{commit}");
            if (head == null) {
                throw new ExorcistException("This repository has no commits yet. No one has died here... yet.");
            }

            Map<String, List<CommitInfo>> history = readHistory(repository, head, prefix);
            List<GhostProfile> ghosts = history.entrySet().stream()
                    .filter(e -> !e.getValue().isEmpty())
                    .map(e -> new GhostProfile(className(e.getKey()), e.getKey(), e.getValue()))
                    .sorted(Comparator.comparing(GhostProfile::path))
                    .toList();
            if (ghosts.isEmpty()) {
                throw new ExorcistException("No Java spirits rest in " + scope + ". Is this really a Java project?");
            }
            boolean shallow = Files.exists(repository.getDirectory().toPath().resolve("shallow"));
            return new Graveyard(workTree, scope, ghosts, shallow);
        }
    }

    private static Repository open(Path directory) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(directory.toFile());
        if (builder.getGitDir() == null) {
            throw new ExorcistException(
                    "No Git history haunts " + directory + ". Point me at a Git repository (or a folder inside one).");
        }
        return builder.setMustExist(true).build();
    }

    /** Path of {@code scope} inside the work tree, as a Git path prefix ("" for the root). */
    private static String relativePrefix(Path workTree, Path scope) {
        String relative = workTree.relativize(scope).toString().replace('\\', '/');
        return relative.isEmpty() ? "" : relative + "/";
    }

    private static Map<String, List<CommitInfo>> readHistory(Repository repository, ObjectId head, String prefix)
            throws IOException {
        try (RevWalk walk = new RevWalk(repository);
             DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diff.setRepository(repository);
            diff.setPathFilter(PathSuffixFilter.create(JAVA_SUFFIX));
            diff.setDetectRenames(true);

            RevCommit headCommit = walk.parseCommit(head);
            Map<String, List<CommitInfo>> history = new LinkedHashMap<>();
            // Path a file had at some point in history -> its path at HEAD. Updated as renames are found.
            Map<String, String> currentPathOf = new HashMap<>();
            for (String path : javaFilesAt(repository, headCommit.getTree(), prefix)) {
                history.put(path, new ArrayList<>());
                currentPathOf.put(path, path);
            }

            AuthorIdentities identities = new AuthorIdentities();
            walk.markStart(headCommit);
            for (RevCommit commit : walk) {
                // Merge commits only repeat changes made on their branches; attributing them would
                // credit whoever merged instead of whoever wrote the code.
                if (commit.getParentCount() > 1) {
                    continue;
                }
                identities.add(commit.getAuthorIdent().getName(), commit.getAuthorIdent().getEmailAddress());
                if (currentPathOf.isEmpty()) {
                    continue;
                }
                RevTree parentTree = commit.getParentCount() == 0 ? null : walk.parseCommit(commit.getParent(0)).getTree();
                CommitInfo info = null;
                for (DiffEntry entry : diff.scan(parentTree, commit.getTree())) {
                    if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                        continue;
                    }
                    String current = currentPathOf.get(entry.getNewPath());
                    if (current == null) {
                        continue;
                    }
                    if (info == null) {
                        info = toCommitInfo(commit);
                    }
                    history.get(current).add(info);
                    switch (entry.getChangeType()) {
                        // Before this commit the path held nothing, or a different file that was deleted.
                        case ADD, COPY -> currentPathOf.remove(entry.getNewPath());
                        // Before this commit the file lived at its old path.
                        case RENAME -> {
                            currentPathOf.remove(entry.getNewPath());
                            currentPathOf.put(entry.getOldPath(), current);
                        }
                        default -> { }
                    }
                }
            }

            Map<String, String> canonical = identities.canonicalNames();
            history.replaceAll((path, commits) -> commits.stream()
                    .map(c -> c.withAuthor(canonical.get(c.author())))
                    .toList());
            return history;
        }
    }

    private static List<String> javaFilesAt(Repository repository, RevTree tree, String prefix) throws IOException {
        List<String> paths = new ArrayList<>();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(JAVA_SUFFIX));
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (path.startsWith(prefix) && !NON_CLASS_FILES.contains(className(path))) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private static CommitInfo toCommitInfo(RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();
        ZonedDateTime when = author.getWhenAsInstant().atZone(author.getZoneId());
        return new CommitInfo(commit.getName(), author.getName(), when, commit.getShortMessage());
    }

    private static String className(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.substring(0, fileName.length() - JAVA_SUFFIX.length());
    }
}
