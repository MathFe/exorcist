package dev.exorcist.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.exorcist.code.LineRange;
import dev.exorcist.code.ParsedClass;

/**
 * The lines an exorcism rewrites, and why they were chosen.
 *
 * <p>Small models rewrite a complete statement or method far more reliably than a fragment of one, and a short
 * one more reliably than a long one, so a sin is widened to the smallest complete code that fixes it.
 */
public record ExorcismTarget(LineRange range, String reason) {

    /** Methods longer than this are too much to rewrite in one answer; the next sin is tried instead. */
    static final int MAX_LINES = 120;

    private static final List<String> WORST_FIRST = List.of(
            "swallowed exception", "broad catch", "console error handling", "deep nesting", "long method",
            "long parameter list", "console output", "legacy API", "deprecated code", "unfinished business");

    public static ExorcismTarget chosenByUser(LineRange range) {
        return new ExorcismTarget(range, "the living developer chose them");
    }

    /**
     * The method holding the ghost's worst confessed sin (confessions are ordered worst first), counting only
     * sins the static scan confirms, since models do confess sins that are not there. Without such a
     * confession, the method holding the worst sin found by the scan.
     */
    public static Optional<ExorcismTarget> choose(ParsedClass parsed, Optional<String> confession) {
        List<ParsedClass.Suspect> suspects = parsed.suspects().stream()
                .filter(s -> WORST_FIRST.contains(s.kind()))
                .sorted(Comparator.comparingInt(s -> WORST_FIRST.indexOf(s.kind())))
                .toList();

        Optional<ExorcismTarget> confessed = confession.stream()
                .flatMap(text -> CitationCheck.citedRanges(text).stream())
                .flatMap(cited -> suspects.stream().filter(s -> overlaps(cited, s)))
                .map(s -> around(parsed, s, "the worst sin you confessed"))
                .flatMap(Optional::stream)
                .findFirst();
        if (confessed.isPresent()) {
            return confessed;
        }
        return suspects.stream()
                .map(s -> around(parsed, s, "the worst sin in this class"))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** Widens a sin to the code that fixes it, telling the model exactly which sin inside it to cast out. */
    private static Optional<ExorcismTarget> around(ParsedClass parsed, ParsedClass.Suspect sin, String why) {
        if (sin.fixScope().size() > MAX_LINES) {
            return Optional.empty();
        }
        String where = parsed.enclosingCallable(sin.beginLine()).map(m -> ", inside " + m.signature()).orElse("");
        String reason = "%s%s; %s at %s: %s".formatted(why, where, sin.kind(),
                new LineRange(sin.beginLine(), sin.endLine()), sin.detail());
        return Optional.of(new ExorcismTarget(sin.fixScope(), reason));
    }

    private static boolean overlaps(LineRange cited, ParsedClass.Suspect suspect) {
        return cited.begin() <= suspect.endLine() && suspect.beginLine() <= cited.end();
    }
}
