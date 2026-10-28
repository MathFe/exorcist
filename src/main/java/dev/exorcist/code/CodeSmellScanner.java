package dev.exorcist.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

/**
 * A quick static scan for classic code smells. Its findings are given to the model as evidence with exact
 * line numbers, so confessions are anchored in the code instead of guessed.
 */
final class CodeSmellScanner {

    static final int LONG_METHOD_LINES = 40;
    static final int MANY_PARAMETERS = 5;
    static final int DEEP_NESTING = 4;
    static final int LARGE_CLASS_LINES = 500;
    static final int MAX_SUSPECTS = 25;

    private static final Set<String> BROAD_EXCEPTIONS = Set.of("Exception", "Throwable", "RuntimeException");
    private static final Set<String> LEGACY_TYPES = Set.of("Vector", "Hashtable", "StringBuffer", "Enumeration", "Stack");
    private static final Pattern TODO = Pattern.compile("\\b(TODO|FIXME|XXX|HACK)\\b.*", Pattern.CASE_INSENSITIVE);

    private CodeSmellScanner() {
    }

    static List<ParsedClass.Suspect> scan(TypeDeclaration<?> type) {
        List<ParsedClass.Suspect> found = new ArrayList<>();

        int classLines = lines(type);
        if (classLines > LARGE_CLASS_LINES) {
            found.add(suspect(type, "large class", classLines + " lines in one class"));
        }

        for (CallableDeclaration<?> callable : type.findAll(CallableDeclaration.class)) {
            String name = callable.getNameAsString() + "()";
            if (lines(callable) > LONG_METHOD_LINES) {
                found.add(suspect(callable, "long method", name + " spans " + lines(callable) + " lines"));
            }
            if (callable.getParameters().size() >= MANY_PARAMETERS) {
                found.add(suspect(callable, "long parameter list", name + " takes " + callable.getParameters().size() + " parameters"));
            }
            if (callable.isAnnotationPresent("Deprecated")) {
                found.add(suspect(callable, "deprecated code", name + " is @Deprecated but still here"));
            }
            deepestNesting(callable).ifPresent(found::add);
        }

        for (CatchClause clause : type.findAll(CatchClause.class)) {
            String caught = clause.getParameter().getTypeAsString();
            if (clause.getBody().getStatements().isEmpty()) {
                found.add(suspect(clause, "swallowed exception", "empty catch block for " + caught));
            }
            if (BROAD_EXCEPTIONS.contains(caught)) {
                found.add(suspect(clause, "broad catch", "catches " + caught + ", hiding what can really go wrong"));
            }
        }

        for (MethodCallExpr call : type.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("printStackTrace") && call.getArguments().isEmpty()) {
                found.add(suspect(call, "console error handling", "printStackTrace() instead of logging or rethrowing"));
            }
            boolean printsToConsole = call.getScope()
                    .filter(FieldAccessExpr.class::isInstance)
                    .map(Node::toString)
                    .filter(scope -> scope.equals("System.out") || scope.equals("System.err"))
                    .isPresent();
            if (printsToConsole) {
                found.add(suspect(call, "console output", call.getScope().get() + "." + call.getNameAsString() + "() instead of a logger"));
            }
        }

        for (FieldDeclaration field : type.findAll(FieldDeclaration.class)) {
            if (field.isPublic() && !field.isFinal() && !isInInterface(field)) {
                field.getVariables().forEach(v ->
                        found.add(suspect(field, "public mutable field", v.getNameAsString() + " can be changed by anyone")));
            }
        }

        type.findAll(ClassOrInterfaceType.class).stream()
                .filter(t -> LEGACY_TYPES.contains(t.getNameAsString()))
                .forEach(t -> found.add(suspect(t, "legacy API", t.getNameAsString() + " predates the Collections framework / StringBuilder")));

        for (Comment comment : type.getAllContainedComments()) {
            Matcher todo = TODO.matcher(comment.getContent());
            if (todo.find()) {
                found.add(suspect(comment, "unfinished business", "comment: " + shorten(todo.group())));
            }
        }

        return found.stream()
                .sorted(Comparator.comparingInt(ParsedClass.Suspect::beginLine))
                .distinct()
                .limit(MAX_SUSPECTS)
                .toList();
    }

    /** The most deeply nested control statement of a method, if it reaches {@link #DEEP_NESTING}. */
    private static Optional<ParsedClass.Suspect> deepestNesting(CallableDeclaration<?> callable) {
        Statement deepest = null;
        int maxDepth = 0;
        for (Statement statement : callable.findAll(Statement.class, CodeSmellScanner::isControl)) {
            int depth = 0;
            for (Node n = statement; n != null && n != callable; n = n.getParentNode().orElse(null)) {
                if (n instanceof Statement s && isControl(s) && !isElseIf(s)) {
                    depth++;
                }
            }
            if (depth > maxDepth) {
                maxDepth = depth;
                deepest = statement;
            }
        }
        return maxDepth >= DEEP_NESTING
                ? Optional.of(suspect(deepest, "deep nesting",
                        callable.getNameAsString() + "() nests control flow " + maxDepth + " levels deep", callable))
                : Optional.empty();
    }

    private static boolean isControl(Statement s) {
        return s instanceof IfStmt || s instanceof ForStmt || s instanceof ForEachStmt || s instanceof WhileStmt
                || s instanceof DoStmt || s instanceof SwitchStmt || s instanceof TryStmt;
    }

    /** {@code else if} is written flat, so it should not count as one more level. */
    private static boolean isElseIf(Statement s) {
        return s instanceof IfStmt
                && s.getParentNode().filter(IfStmt.class::isInstance)
                .map(IfStmt.class::cast)
                .flatMap(IfStmt::getElseStmt)
                .filter(elseStmt -> elseStmt == s)
                .isPresent();
    }

    private static boolean isInInterface(FieldDeclaration field) {
        return field.getParentNode()
                .filter(p -> p instanceof ClassOrInterfaceDeclaration c && c.isInterface())
                .isPresent();
    }

    private static ParsedClass.Suspect suspect(Node node, String kind, String detail) {
        return suspect(node, kind, detail, fixNode(node));
    }

    private static ParsedClass.Suspect suspect(Node node, String kind, String detail, Node fix) {
        return new ParsedClass.Suspect(kind, detail, beginLine(node), endLine(node),
                new LineRange(beginLine(fix), Math.max(beginLine(fix), endLine(fix))));
    }

    /**
     * The smallest complete piece of code to rewrite to fix a smell found at {@code node}. Small models
     * rewrite a whole statement or method well, and a fragment of one badly.
     */
    private static Node fixNode(Node node) {
        if (node instanceof CatchClause clause) {
            return clause.getParentNode().orElse(clause);
        }
        if (node instanceof CallableDeclaration<?> || node instanceof TypeDeclaration<?> || node instanceof FieldDeclaration) {
            return node;
        }
        if (node instanceof Comment comment) {
            return comment.getCommentedNode().or(comment::getParentNode).map(CodeSmellScanner::fixNode).orElse(comment);
        }
        for (Node n = node.getParentNode().orElse(null); n != null; n = n.getParentNode().orElse(null)) {
            if ((n instanceof Statement && !(n instanceof BlockStmt))
                    || n instanceof FieldDeclaration || n instanceof CallableDeclaration<?>) {
                return n;
            }
        }
        return node;
    }

    private static int beginLine(Node node) {
        return node.getBegin().map(p -> p.line).orElse(1);
    }

    private static int endLine(Node node) {
        return node.getEnd().map(p -> p.line).orElse(beginLine(node));
    }

    private static int lines(Node node) {
        return node.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);
    }

    private static String shorten(String text) {
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 77) + "...";
    }
}
