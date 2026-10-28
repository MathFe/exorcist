package dev.exorcist.code;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.springframework.stereotype.Service;

/**
 * Splices replacement code into a file and renders the change as a unified diff.
 *
 * <p>The model only says which lines to replace and with what; the diff itself is computed here, so it is
 * always well-formed and can be applied with {@code git apply}.
 */
@Service
public class DiffService {

    private final CodeParserService parser;

    public DiffService(CodeParserService parser) {
        this.parser = parser;
    }

    /**
     * The proposed change, never written to disk.
     *
     * @param whitespaceOnly true when the change only moves whitespace around: no sin was cast out
     */
    public record Patch(String diff, List<String> after, boolean stillParses, boolean whitespaceOnly) {

        public boolean isEmpty() {
            return diff.isBlank();
        }
    }

    public Patch replace(String path, List<String> before, LineRange range, List<String> replacement) {
        List<String> code = reindent(replacement, before.subList(range.begin() - 1, range.end()));
        List<String> after = new ArrayList<>(before.subList(0, range.begin() - 1));
        after.addAll(code);
        after.addAll(before.subList(range.end(), before.size()));

        String diff = unifiedDiff(path, before, after);
        boolean whitespaceOnly = withoutWhitespace(before).equals(withoutWhitespace(after));
        return new Patch(diff, after, parser.parses(String.join("\n", after)), whitespaceOnly);
    }

    private static List<String> withoutWhitespace(List<String> lines) {
        return lines.stream().map(line -> line.replaceAll("\\s+", "")).filter(line -> !line.isEmpty()).toList();
    }

    private static EditList edits(List<String> before, List<String> after) {
        return DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
                .diff(RawTextComparator.DEFAULT, rawText(before), rawText(after));
    }

    String unifiedDiff(String path, List<String> before, List<String> after) {
        EditList edits = edits(before, after);
        if (edits.isEmpty()) {
            return "";
        }
        RawText a = rawText(before);
        RawText b = rawText(after);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.format(edits, a, b);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "--- a/" + path + "\n+++ b/" + path + "\n" + out.toString(StandardCharsets.UTF_8);
    }

    /**
     * Models often drop the indentation of the code they rewrite. If the replacement is indented less than
     * the lines it replaces, shift it right so it sits at the same depth.
     */
    static List<String> reindent(List<String> replacement, List<String> original) {
        int expected = minIndent(original);
        int actual = minIndent(replacement);
        if (actual >= expected) {
            return replacement;
        }
        String pad = " ".repeat(expected - actual);
        return replacement.stream().map(line -> line.isBlank() ? line : pad + line).toList();
    }

    private static int minIndent(List<String> lines) {
        return lines.stream()
                .filter(line -> !line.isBlank())
                .mapToInt(line -> line.length() - line.stripLeading().length())
                .min()
                .orElse(0);
    }

    private static RawText rawText(List<String> lines) {
        String text = lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
        return new RawText(text.getBytes(StandardCharsets.UTF_8));
    }
}
