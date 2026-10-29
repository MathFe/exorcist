package dev.exorcist.code;

import java.util.List;

import dev.exorcist.ExorcistException;
import dev.exorcist.Fixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeParserServiceTest {

    private final CodeParserService parser = new CodeParserService();

    @Test
    void readsTheClassHeaderAndItsPlaceInTheFile() {
        ParsedClass parsed = parser.parse(Fixtures.COUNTER, "Counter");

        assertThat(parsed.parsed()).isTrue();
        assertThat(parsed.qualifiedName()).isEqualTo("org.example.Counter");
        assertThat(parsed.declaration()).isEqualTo("public class Counter extends Object implements Runnable");
        assertThat(parsed.summary()).isEqualTo("Counts sheep.");
        // The class starts at its Javadoc; the readable code starts after the license header.
        assertThat(parsed.beginLine()).isEqualTo(8);
        assertThat(parsed.endLine()).isEqualTo(30);
        assertThat(parsed.codeStartLine()).isEqualTo(4);
        assertThat(parsed.lines()).hasSize(30);
    }

    @Test
    void mapsMembersToTheirLines() {
        List<ParsedClass.Member> members = parser.parse(Fixtures.COUNTER, "Counter").members();

        assertThat(members).containsExactly(
                new ParsedClass.Member("field", "public int count", 10, 10),
                new ParsedClass.Member("field", "private final Vector<String> names", 11, 11),
                new ParsedClass.Member("constructor", "public Counter()", 13, 14),
                new ParsedClass.Member("method", "public void run()", 16, 22),
                new ParsedClass.Member("method", "void log()", 24, 27),
                new ParsedClass.Member("nested enum", "Mood", 29, 29));
    }

    @Test
    void findsNestedTypes() {
        ParsedClass mood = parser.parse(Fixtures.COUNTER, "Mood");

        assertThat(mood.declaration()).isEqualTo("enum Mood");
        assertThat(mood.members()).containsExactly(new ParsedClass.Member("constants", "CALM, ANGRY", 29, 29));
    }

    @Test
    void findsTheInnermostCallableAroundALine() {
        ParsedClass parsed = parser.parse(Fixtures.COUNTER, "Counter");

        assertThat(parsed.enclosingCallable(19)).map(ParsedClass.Member::signature).contains("public void run()");
        assertThat(parsed.enclosingCallable(10)).isEmpty();
    }

    @Test
    void keepsTheRawLinesWhenTheFileDoesNotParse() {
        ParsedClass parsed = parser.parse("class Broken {\n  void f( {\n}\n", "Broken");

        assertThat(parsed.parsed()).isFalse();
        assertThat(parsed.lines()).hasSize(3);
        assertThat(parsed.members()).isEmpty();
        assertThat(parser.parses("class Broken {\n  void f( {\n}\n")).isFalse();
    }

    @Test
    void parsesCodeFromAnyJavaVersion() {
        // Java 1.4 code that uses 'enum' as a name, and Java 21 code, both parse.
        assertThat(parser.parses("class Old { java.util.Enumeration enum = null; }")).isTrue();
        assertThat(parser.parses("record Point(int x, int y) { }")).isTrue();
    }

    @Test
    void rejectsAClassThatIsNotInTheFile() {
        assertThatThrownBy(() -> parser.parse(Fixtures.COUNTER, "Sheep"))
                .isInstanceOf(ExorcistException.class)
                .hasMessageContaining("not by Sheep");
    }
}
