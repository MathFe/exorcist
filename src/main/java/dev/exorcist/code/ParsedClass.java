package dev.exorcist.code;

import java.util.List;

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
 * @param parsed        false when JavaParser could not parse the file and only the raw lines are known
 */
public record ParsedClass(String name, String packageName, String declaration, int beginLine, int endLine,
                          List<Member> members, List<String> lines, int codeStartLine, boolean parsed) {

    public ParsedClass {
        members = List.copyOf(members);
        lines = List.copyOf(lines);
    }

    public String qualifiedName() {
        return packageName.isEmpty() ? name : packageName + "." + name;
    }

    /** One member of the class, e.g. {@code method: public static void fail(String message)} at lines 87-92. */
    public record Member(String kind, String signature, int beginLine, int endLine) {
    }
}
