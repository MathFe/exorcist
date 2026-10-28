package dev.exorcist.ai;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.SequencedSet;
import java.util.stream.Collectors;

import dev.exorcist.ExorcistProperties;
import dev.exorcist.Possession;
import dev.exorcist.code.ParsedClass;
import dev.exorcist.git.CommitInfo;
import dev.exorcist.git.FileSnapshot.LineBlame;
import dev.exorcist.git.GhostProfile;
import org.springframework.stereotype.Component;

/**
 * Builds the prompts sent to the model. The system prompt gives it the ghost's persona and the rules;
 * the user prompt carries the facts (Git history, class outline, numbered source) and the task.
 *
 * <p>Small models follow structure better than prose, so the facts go in clearly labelled sections
 * that the rules can refer to by name.
 */
@Component
public class PromptBuilder {

    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd 'at' HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_MESSAGE_LENGTH = 120;
    private static final int MAX_OTHER_HANDS = 15;

    private static final String PERSONA = """
            You are the ghost of %1$s, a Java developer who haunts the file %2$s.
            A living developer who inherited your code has summoned you.

            How you speak:
            - In first person, in English, as %1$s. Theatrical, gothic and a little funny: you are a ghost, after all.
            - Plain text for a terminal: short paragraphs, no Markdown headings, tables or bold. `Backticks` for code are fine.

            Rules you cannot break, even in death:
            1. Every statement about the code cites the lines it is about, as (line 42) or (lines 42-57), \
            using the numbers in the SOURCE CODE listing. No line number, no claim.
            2. Only talk about code that is in the listing. Never invent methods, fields, classes or behaviour. \
            If part of the file was cut, say so instead of guessing.
            3. Your story comes only from GIT MEMORIES: its dates, weekdays, commit messages and authors are facts. \
            Do not invent other events. You may muse about your feelings ("I must have been tired"), never about facts.
            4. The drama is in the tone only. The technical explanation must be correct.
            """;

    /** Repeated at the very end of each task: small models weigh the last instructions most. */
    private static final String CITATION_REMINDER = """

            Write every sentence about the code in this shape, with the line numbers in parentheses:
            "<what this part of the code does> (lines 40-44)."
            "<what this single line does> (line 12)."
            Remember: no line number, no claim.
            """;

    private static final String POSSESS_TASK = """
            === YOUR TASK ===
            You have just possessed this class. In about 250 words:
            1. Introduce yourself using GIT MEMORIES: who you are, when this file was born and when you last touched it. \
            If someone else created the file, say so.
            2. Explain what the class does and how it works, walking through its most important members. \
            Cite line numbers for every claim.
            3. Finish with one sentence of advice for the living developer who inherited it.
            """ + CITATION_REMINDER;

    private final ExorcistProperties limits;

    public PromptBuilder(ExorcistProperties limits) {
        this.limits = limits;
    }

    public GhostPrompt possess(Possession possession) {
        return new GhostPrompt(persona(possession), facts(possession) + POSSESS_TASK);
    }

    String persona(Possession possession) {
        return PERSONA.formatted(possession.spirit(), possession.ghost().path());
    }

    /** The shared context of every prompt about a possessed class. */
    String facts(Possession possession) {
        return gitMemories(possession) + "\n" + codeStructure(possession.parsed()) + "\n" + sourceCode(possession.parsed()) + "\n";
    }

    private String gitMemories(Possession possession) {
        GhostProfile ghost = possession.ghost();
        String spirit = possession.spirit();
        List<CommitInfo> spiritCommits = ghost.commitsBy(spirit);

        StringBuilder out = new StringBuilder("=== GIT MEMORIES (facts from the repository history) ===\n");
        out.append("File: ").append(ghost.path()).append('\n');
        out.append("Class: ").append(possession.parsed().qualifiedName()).append('\n');
        out.append("You are the spirit of: %s, the author with the most commits on this file (%d of %d)%n"
                .formatted(spirit, spiritCommits.size(), ghost.commits().size()));
        out.append("File born: ").append(moment(ghost.born())).append('\n');
        out.append("Your first commit here: ").append(moment(spiritCommits.getLast())).append('\n');
        out.append("Your last commit here: ").append(moment(spiritCommits.getFirst())).append('\n');
        out.append("Last change by anyone: ").append(moment(ghost.died())).append('\n');
        out.append("Authors: ").append(ghost.commitsByAuthor().entrySet().stream()
                .map(e -> "%s (%d %s)".formatted(e.getKey(), e.getValue(), e.getValue() == 1 ? "commit" : "commits"))
                .collect(Collectors.joining(", "))).append('\n');
        out.append(lineOwnership(possession.snapshot().blame(), spirit));
        out.append("Commit messages, oldest first:\n");
        for (CommitInfo commit : memorableCommits(ghost, spiritCommits)) {
            out.append("- %s %s: %s%n".formatted(DAY.format(commit.when()), commit.author(), shorten(commit.message())));
        }
        return out.toString();
    }

    /** How much of the living code the spirit wrote, and which passages belong to others (from blame). */
    private static String lineOwnership(List<LineBlame> blame, String spirit) {
        if (blame.isEmpty()) {
            return "";
        }
        long mine = blame.stream().filter(line -> line.author().equals(spirit)).count();
        StringBuilder out = new StringBuilder("Lines alive at HEAD: %d. Last written by you: %d (%d%%).%n"
                .formatted(blame.size(), mine, Math.round(100.0 * mine / blame.size())));

        List<Passage> others = new ArrayList<>();
        for (int i = 0; i < blame.size(); i++) {
            LineBlame line = blame.get(i);
            if (line.author().equals(spirit)) {
                continue;
            }
            Passage last = others.isEmpty() ? null : others.getLast();
            if (last != null && last.end() == i && last.author().equals(line.author())) {
                others.set(others.size() - 1, last.extendTo(i + 1, line));
            } else {
                others.add(new Passage(line.author(), i + 1, i + 1, line.when() == null ? 0 : line.when().getYear()));
            }
        }
        if (!others.isEmpty()) {
            List<Passage> largest = others.stream()
                    .sorted(Comparator.comparingInt(Passage::size).reversed())
                    .limit(MAX_OTHER_HANDS)
                    .sorted(Comparator.comparingInt(Passage::begin))
                    .toList();
            out.append("Lines last written by other hands: ")
                    .append(largest.stream().map(Passage::toString).collect(Collectors.joining(", ")));
            if (others.size() > largest.size()) {
                out.append(" (and %d smaller passages)".formatted(others.size() - largest.size()));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** The file's birth and last change, then the spirit's own commits, then everyone else's; oldest first. */
    private List<CommitInfo> memorableCommits(GhostProfile ghost, List<CommitInfo> spiritCommits) {
        SequencedSet<CommitInfo> chosen = new LinkedHashSet<>();
        chosen.add(ghost.born());
        chosen.add(ghost.died());
        chosen.addAll(spiritCommits);
        chosen.addAll(ghost.commits());
        return chosen.stream()
                .limit(limits.maxCommitMessages())
                .sorted(Comparator.comparing(CommitInfo::when))
                .toList();
    }

    private String codeStructure(ParsedClass parsed) {
        StringBuilder out = new StringBuilder("=== CODE STRUCTURE ===\n");
        if (!parsed.parsed()) {
            return out.append("The parser could not read this file, so there is no outline. Rely on the listing.\n").toString();
        }
        out.append("%s (lines %d-%d)%s%n".formatted(parsed.declaration(), parsed.beginLine(), parsed.endLine(),
                parsed.packageName().isEmpty() ? ", default package" : ", package " + parsed.packageName()));
        List<ParsedClass.Member> members = parsed.members();
        members.stream().limit(limits.maxOutlineMembers()).forEach(m ->
                out.append("- %s %s: %s%n".formatted(m.kind(), m.signature(), range(m.beginLine(), m.endLine()))));
        if (members.size() > limits.maxOutlineMembers()) {
            out.append("- ... and %d more members%n".formatted(members.size() - limits.maxOutlineMembers()));
        }
        return out.toString();
    }

    private String sourceCode(ParsedClass parsed) {
        List<String> lines = parsed.lines();
        int first = Math.min(parsed.codeStartLine(), Math.max(1, lines.size()));
        int last = Math.min(lines.size(), first + limits.maxSourceLines() - 1);
        int width = String.valueOf(last).length();

        StringBuilder out = new StringBuilder("=== SOURCE CODE (line numbers on the left");
        if (first > 1) {
            out.append("; lines 1-").append(first - 1).append(" are the license header and are omitted");
        }
        out.append(") ===\n");
        for (int n = first; n <= last; n++) {
            out.append(String.format("%" + width + "d| %s%n", n, lines.get(n - 1)));
        }
        if (last < lines.size()) {
            out.append("[lines %d-%d were cut to fit in memory; CODE STRUCTURE shows what they contain]%n"
                    .formatted(last + 1, lines.size()));
        }
        return out.toString();
    }

    private static String moment(CommitInfo commit) {
        return "%s, by %s, commit %s \"%s\"".formatted(
                MOMENT.format(commit.when()), commit.author(), commit.shortId(), shorten(commit.message()));
    }

    private static String range(int begin, int end) {
        return begin == end ? "line " + begin : "lines " + begin + "-" + end;
    }

    private static String shorten(String text) {
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= MAX_MESSAGE_LENGTH ? oneLine : oneLine.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    /** A run of consecutive lines last written by the same other author. */
    private record Passage(String author, int begin, int end, int year) {

        Passage extendTo(int newEnd, LineBlame line) {
            int lineYear = line.when() == null ? year : Math.max(year, line.when().getYear());
            return new Passage(author, begin, newEnd, lineYear);
        }

        int size() {
            return end - begin + 1;
        }

        @Override
        public String toString() {
            return range(begin, end) + " " + author + (year > 0 ? " (" + year + ")" : "");
        }
    }
}
