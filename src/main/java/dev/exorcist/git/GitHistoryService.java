package dev.exorcist.git;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.exorcist.ExorcistException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
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
    private static final Set<String> IGNORED_DIRECTORIES = Set.of("target", "build", "out", "bin", "node_modules");

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

            AuthorIdentities identities = new AuthorIdentities();
            Map<String, List<CommitInfo>> history = readHistory(repository, head, prefix, identities);
            Map<String, String> canonical = identities.canonicalNames();
            List<GhostProfile> ghosts = history.entrySet().stream()
                    .filter(e -> !e.getValue().isEmpty())
                    .map(e -> new GhostProfile(className(e.getKey()), e.getKey(), e.getValue().stream()
                            .map(c -> c.withAuthor(canonical.get(c.author())))
                            .toList()))
                    .sorted(Comparator.comparing(GhostProfile::path))
                    .toList();
            if (ghosts.isEmpty()) {
                throw new ExorcistException("No Java spirits rest in " + scope + ". Is this really a Java project?");
            }
            boolean shallow = Files.exists(repository.getDirectory().toPath().resolve("shallow"));
            return new Graveyard(workTree, scope, ghosts, shallow, canonical);
        }
    }

    /** Reads a ghost's file as it is at HEAD, with {@code git blame} for every line. */
    public FileSnapshot snapshot(Graveyard graveyard, GhostProfile ghost) {
        try (Repository repository = open(graveyard.workTree());
             Git git = new Git(repository);
             RevWalk walk = new RevWalk(repository)) {
            RevCommit head = walk.parseCommit(repository.resolve("HEAD^{commit}"));
            String content;
            try (TreeWalk file = TreeWalk.forPath(repository, ghost.path(), head.getTree())) {
                if (file == null) {
                    throw new ExorcistException("The spirit of " + ghost.className()
                            + " has left HEAD since the summon. Run summon again.");
                }
                content = decode(repository.open(file.getObjectId(0)).getBytes());
            }

            BlameResult blame = git.blame()
                    .setFilePath(ghost.path())
                    .setStartCommit(head)
                    .setFollowFileRenames(true)
                    .call();
            List<FileSnapshot.LineBlame> lines = new ArrayList<>();
            for (int i = 0; blame != null && i < blame.getResultContents().size(); i++) {
                PersonIdent author = blame.getSourceAuthor(i);
                lines.add(author == null
                        ? new FileSnapshot.LineBlame("an unknown hand", null)
                        : new FileSnapshot.LineBlame(graveyard.canonicalAuthor(author.getName()),
                                author.getWhenAsInstant().atZone(author.getZoneId())));
            }
            return new FileSnapshot(content, lines);
        } catch (IOException | GitAPIException e) {
            throw new ExorcistException("The spirit's memories of " + ghost.path() + " are unreadable: " + e.getMessage(), e);
        }
    }

    /**
     * Looks on disk for a Java file that Git does not know about, to tell "never committed" apart
     * from "does not exist". Skips build output and hidden folders.
     */
    public Optional<Path> findUntracked(Graveyard graveyard, String className) {
        String fileName = className.substring(className.lastIndexOf('.') + 1) + JAVA_SUFFIX;
        List<Path> found = new ArrayList<>();
        try {
            Files.walkFileTree(graveyard.scope(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    boolean skip = !dir.equals(graveyard.scope())
                            && (name.startsWith(".") || IGNORED_DIRECTORIES.contains(name));
                    return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equalsIgnoreCase(fileName)) {
                        found.add(file);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return Optional.empty();
        }
        return found.stream().findFirst();
    }

    /** Old code is often Latin-1: use UTF-8 when the bytes are valid UTF-8, Latin-1 otherwise. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
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

    /**
     * Walks every commit reachable from HEAD and returns, per Java file at HEAD, the commits that touched it.
     * Author names are returned as found in the commits; {@code identities} learns who is who along the way.
     */
    private static Map<String, List<CommitInfo>> readHistory(Repository repository, ObjectId head, String prefix,
                                                             AuthorIdentities identities) throws IOException {
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
