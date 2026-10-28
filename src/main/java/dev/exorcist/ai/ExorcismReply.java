package dev.exorcist.ai;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.exorcist.code.LineRange;

/**
 * The model's answer to {@code exorcise}, in the format the prompt asks for:
 * a short explanation, a {@code REPLACE LINES a-b} line and one Java code block.
 */
public record ExorcismReply(String explanation, LineRange range, List<String> code) {

    private static final Pattern REPLACE = Pattern.compile("REPLACE\\s+LINES?\\s*:?\\s*(\\d+)\\s*(?:[-–]\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_BLOCK = Pattern.compile("```[a-zA-Z]*\\s*\\R(.*?)```", Pattern.DOTALL);

    /** Empty when the reply does not follow the format, so the caller can tell the user instead of guessing. */
    public static Optional<ExorcismReply> parse(String reply) {
        Matcher replace = REPLACE.matcher(reply);
        if (!replace.find()) {
            return Optional.empty();
        }
        int begin = Integer.parseInt(replace.group(1));
        int end = replace.group(2) == null ? begin : Integer.parseInt(replace.group(2));
        if (begin < 1 || end < begin) {
            return Optional.empty();
        }
        // Small models sometimes forget the fences: then the code is whatever follows the REPLACE line.
        String afterReplace = reply.substring(replace.end());
        Matcher block = CODE_BLOCK.matcher(afterReplace);
        String codeText = block.find()
                ? block.group(1)
                : afterReplace.replaceFirst("^[^\\n]*\\n", "").replaceFirst("^\\s*```[a-zA-Z]*\\s*\\n", "");
        if (codeText.isBlank()) {
            return Optional.empty();
        }
        List<String> code = codeText.stripTrailing().lines().dropWhile(String::isBlank).toList();
        String explanation = reply.substring(0, replace.start()).strip();
        return Optional.of(new ExorcismReply(explanation, new LineRange(begin, end), code));
    }
}
