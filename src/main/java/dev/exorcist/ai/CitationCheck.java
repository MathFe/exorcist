package dev.exorcist.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.exorcist.code.LineRange;

/**
 * Checks the model's answer against the "no line number, no claim" rule: counts its citations, flags
 * the ones pointing outside the file and the numbered list items that cite nothing.
 *
 * @param citations  how many line citations the text contains
 * @param outOfRange cited lines that do not exist in the file
 * @param uncited    numbered list items (e.g. confessions) without any line citation
 */
public record CitationCheck(int citations, List<Integer> outOfRange, int uncited) {

    private static final Pattern CITATION = Pattern.compile("\\blines?\\s+(\\d+)(?:\\s*(?:-|–|to)\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_ITEM = Pattern.compile("(?m)^\\s*\\d+[.)]\\s");

    public static CitationCheck of(String text, int lineCount) {
        int citations = 0;
        List<Integer> outOfRange = new ArrayList<>();
        Matcher m = CITATION.matcher(text);
        while (m.find()) {
            citations++;
            for (int g = 1; g <= 2; g++) {
                if (m.group(g) != null) {
                    int line = Integer.parseInt(m.group(g));
                    if ((line < 1 || line > lineCount) && !outOfRange.contains(line)) {
                        outOfRange.add(line);
                    }
                }
            }
        }
        return new CitationCheck(citations, outOfRange, uncitedItems(text));
    }

    /** Every line range cited in the text, in order, e.g. 95-142 for "1. (lines 95-142) Long method...". */
    public static List<LineRange> citedRanges(String text) {
        List<LineRange> ranges = new ArrayList<>();
        Matcher m = CITATION.matcher(text);
        while (m.find()) {
            int begin = Integer.parseInt(m.group(1));
            int end = m.group(2) == null ? begin : Integer.parseInt(m.group(2));
            if (begin >= 1 && end >= begin) {
                ranges.add(new LineRange(begin, end));
            }
        }
        return ranges;
    }

    public boolean clean() {
        return citations > 0 && outOfRange.isEmpty() && uncited == 0;
    }

    private static int uncitedItems(String text) {
        Matcher m = LIST_ITEM.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        int uncited = 0;
        for (int i = 0; i < starts.size(); i++) {
            String item = text.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : text.length());
            if (!CITATION.matcher(item).find()) {
                uncited++;
            }
        }
        return uncited;
    }
}
