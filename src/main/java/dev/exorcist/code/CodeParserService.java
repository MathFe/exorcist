package dev.exorcist.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import dev.exorcist.ExorcistException;
import org.springframework.stereotype.Service;

/** Finds a class in a Java source file and maps out its members by line number. */
@Service
public class CodeParserService {

    /** RAW skips language-level validation, so code written for Java 1.4 and Java 21 both parse. */
    private final JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.RAW));

    /** True when the source is valid Java, used to check that an exorcised file still parses. */
    public boolean parses(String source) {
        return parser.parse(source).isSuccessful();
    }

    public ParsedClass parse(String source, String className) {
        List<String> lines = source.lines().toList();
        ParseResult<CompilationUnit> result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            // Still useful: the model gets numbered lines, just without the outline.
            return new ParsedClass(className, "", className, 1, lines.size(), List.of(), List.of(), lines, 1, "", false);
        }
        CompilationUnit unit = result.getResult().get();
        TypeDeclaration<?> type = findType(unit, className).orElseThrow(() -> new ExorcistException(
                "The file is haunted, but not by " + className + ". No such class is declared in it."));

        return new ParsedClass(
                type.getNameAsString(),
                unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse(""),
                declaration(type),
                beginLine(type),
                endLine(type),
                members(type),
                CodeSmellScanner.scan(type),
                lines,
                codeStartLine(unit),
                summary(type),
                true);
    }

    /** First sentence of the class Javadoc: "A Decorator that runs a test repeatedly." */
    private static String summary(TypeDeclaration<?> type) {
        String text = type.getJavadoc()
                .map(javadoc -> javadoc.getDescription().toText())
                .orElse("")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\{@\\w+\\s+([^}]*)}", "$1")
                .replaceAll("\\s+", " ")
                .strip();
        int end = text.indexOf(". ");
        return end < 0 ? text : text.substring(0, end + 1);
    }

    private static Optional<TypeDeclaration<?>> findType(CompilationUnit unit, String className) {
        Optional<TypeDeclaration<?>> topLevel = unit.getTypes().stream()
                .filter(t -> t.getNameAsString().equals(className))
                .findFirst();
        if (topLevel.isPresent()) {
            return topLevel;
        }
        return unit.findAll(TypeDeclaration.class).stream()
                .filter(t -> t.getNameAsString().equals(className))
                .<TypeDeclaration<?>>map(t -> t)
                .findFirst();
    }

    private static String declaration(TypeDeclaration<?> type) {
        StringBuilder header = new StringBuilder(modifiers(type)).append(kind(type)).append(' ').append(type.getNameAsString());
        if (type instanceof ClassOrInterfaceDeclaration c) {
            if (!c.getExtendedTypes().isEmpty()) {
                header.append(" extends ").append(join(c.getExtendedTypes().stream().map(Node::toString)));
            }
            if (!c.getImplementedTypes().isEmpty()) {
                header.append(" implements ").append(join(c.getImplementedTypes().stream().map(Node::toString)));
            }
        }
        return header.toString();
    }

    private static List<ParsedClass.Member> members(TypeDeclaration<?> type) {
        List<ParsedClass.Member> members = new ArrayList<>();
        if (type instanceof EnumDeclaration e && !e.getEntries().isEmpty()) {
            members.add(new ParsedClass.Member("constants",
                    join(e.getEntries().stream().map(c -> c.getNameAsString())),
                    beginLine(e.getEntries().get(0)), endLine(e.getEntries().get(e.getEntries().size() - 1))));
        }
        for (BodyDeclaration<?> member : type.getMembers()) {
            switch (member) {
                case FieldDeclaration f -> f.getVariables().forEach(v -> members.add(new ParsedClass.Member(
                        "field", modifiers(f) + v.getTypeAsString() + " " + v.getNameAsString(), beginLine(f), endLine(f))));
                // strip(): without modifiers, JavaParser leaves a leading space (" void log()").
                case ConstructorDeclaration c -> members.add(new ParsedClass.Member(
                        "constructor", c.getDeclarationAsString(true, false, true).strip(), beginLine(c), endLine(c)));
                case MethodDeclaration m -> members.add(new ParsedClass.Member(
                        "method", m.getDeclarationAsString(true, false, true).strip(), beginLine(m), endLine(m)));
                case TypeDeclaration<?> t -> members.add(new ParsedClass.Member(
                        "nested " + kind(t), t.getNameAsString(), beginLine(t), endLine(t)));
                case InitializerDeclaration i -> members.add(new ParsedClass.Member(
                        i.isStatic() ? "static initializer" : "initializer", "{ ... }", beginLine(i), endLine(i)));
                default -> { }
            }
        }
        return members;
    }

    private static String kind(TypeDeclaration<?> type) {
        return switch (type) {
            case ClassOrInterfaceDeclaration c when c.isInterface() -> "interface";
            case ClassOrInterfaceDeclaration c -> "class";
            case EnumDeclaration e -> "enum";
            case RecordDeclaration r -> "record";
            case AnnotationDeclaration a -> "@interface";
            default -> "type";
        };
    }

    private static String modifiers(NodeWithModifiers<?> node) {
        return node.getModifiers().stream().map(m -> m.getKeyword().asString() + " ").collect(Collectors.joining());
    }

    /**
     * First line after the license header: the package, first import or first type, whichever comes first.
     * The license is usually attached as the package's comment, so the package's own line is used.
     */
    private static int codeStartLine(CompilationUnit unit) {
        return Stream.of(
                        unit.getPackageDeclaration().flatMap(Node::getBegin).map(p -> p.line),
                        unit.getImports().stream().findFirst().flatMap(Node::getBegin).map(p -> p.line),
                        unit.getTypes().stream().findFirst().map(CodeParserService::beginLine))
                .flatMap(Optional::stream)
                .min(Integer::compare)
                .orElse(1);
    }

    /** Begin line including the node's Javadoc, which belongs to it but is not part of its range. */
    private static int beginLine(Node node) {
        int begin = node.getBegin().map(p -> p.line).orElse(1);
        return node.getComment()
                .flatMap(Node::getBegin)
                .map(p -> Math.min(p.line, begin))
                .orElse(begin);
    }

    private static int endLine(Node node) {
        return node.getEnd().map(p -> p.line).orElse(1);
    }

    private static String join(Stream<String> parts) {
        return parts.collect(Collectors.joining(", "));
    }
}
