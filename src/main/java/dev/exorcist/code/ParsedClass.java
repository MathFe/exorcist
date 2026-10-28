package dev.exorcist.code;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A class located in its source file, with the line ranges of its members.
 *
 * @param name          simple class name
 * @param packageName   declared package, or empty for the default package
 * @param declaration   the class header, e.g. {@code public class Assert extends Object}
 * @param beginLine     first line of the class declaration
 * @param endLine       last line of the class declaration
 * @param members       fields, constructors, methods and nested types, in source order
 * @param lines         every line of the file; {@code lines.get(0)} is line 1
 * @param codeStartLine first line worth reading: the license header comment above it is skipped
 * @param suspects      code smells found by a static scan, by line
 * @param parsed        false when JavaParser could not parse the file and only the raw lines are known
 */
public record ParsedClass(String name, String packageName, String declaration, int beginLine, int endLine,
                          List<Member> members, List<Suspect> suspects, List<String> lines, int codeStartLine,
                          boolean parsed) {

    private static final Set<String> CALLABLE_KINDS = Set.of("method", "constructor", "initializer", "static initializer");

    public ParsedClass {
        members = List.copyOf(members);
        suspects = List.copyOf(suspects);
        lines = List.copyOf(lines);
    }

    public String qualifiedName() {
        return packageName.isEmpty() ? name : packageName + "." + name;
    }

    /** The innermost method, constructor or initializer containing the line, if any. */
    public Optional<Member> enclosingCallable(int line) {
        return members.stream()
                .filter(m -> CALLABLE_KINDS.contains(m.kind()))
                .filter(m -> m.beginLine() <= line && line <= m.endLine())
                .min(Comparator.comparingInt(m -> m.endLine() - m.beginLine()));
    }

    /** One member of the class, e.g. {@code method: public static void fail(String message)} at lines 87-92. */
    public record Member(String kind, String signature, int beginLine, int endLine) {
    }

    /**
     * A spot that looks like a code smell, e.g. {@code long method: run() spans 56 lines} at lines 40-95.
     *
     * @param fixScope the smallest complete piece of code to rewrite to fix it: the whole {@code try} statement
     *                 for a bad catch, the statement for a console call, the method for a long method
     */
    public record Suspect(String kind, String detail, int beginLine, int endLine, LineRange fixScope) {
    }
}
