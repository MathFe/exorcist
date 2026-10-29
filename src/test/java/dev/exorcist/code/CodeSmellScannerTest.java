package dev.exorcist.code;

import java.util.List;

import dev.exorcist.Fixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSmellScannerTest {

    private final CodeParserService parser = new CodeParserService();

    private List<ParsedClass.Suspect> scan(String source, String className) {
        return parser.parse(source, className).suspects();
    }

    @Test
    void findsTheSinsOfTheCounter() {
        List<ParsedClass.Suspect> suspects = scan(Fixtures.COUNTER, "Counter");

        assertThat(suspects).extracting(ParsedClass.Suspect::kind).containsExactly(
                "public mutable field", "legacy API", "swallowed exception", "broad catch",
                "unfinished business", "console output");
        // Sorted by line, and the two mentions of Vector on line 11 count once.
        assertThat(suspects).extracting(ParsedClass.Suspect::beginLine).containsExactly(10, 11, 20, 20, 24, 26);
    }

    @Test
    void widensEachSinToTheCodeThatFixesIt() {
        List<ParsedClass.Suspect> suspects = scan(Fixtures.COUNTER, "Counter");

        assertThat(fixScope(suspects, "swallowed exception")).isEqualTo(new LineRange(18, 21)); // the whole try
        assertThat(fixScope(suspects, "console output")).isEqualTo(new LineRange(26, 26));       // the statement
        assertThat(fixScope(suspects, "legacy API")).isEqualTo(new LineRange(11, 11));           // the field
        assertThat(fixScope(suspects, "unfinished business")).isEqualTo(new LineRange(25, 27));  // the commented method
    }

    @Test
    void findsLongMethodsLongParameterListsAndDeprecatedCode() {
        String body = "        int x = 0;\n".repeat(CodeSmellScanner.LONG_METHOD_LINES);
        String source = """
                class Big {
                    @Deprecated
                    void sprawl(int a, int b, int c, int d, int e) {
                %s    }
                }
                """.formatted(body);

        assertThat(scan(source, "Big"))
                .extracting(ParsedClass.Suspect::kind)
                .containsExactlyInAnyOrder("long method", "long parameter list", "deprecated code");
    }

    @Test
    void measuresNestingWithoutCountingElseIf() {
        String flat = """
                class Flat {
                    void f(int x) {
                        if (x == 1) {
                        } else if (x == 2) {
                        } else if (x == 3) {
                        } else if (x == 4) {
                        }
                    }
                }
                """;
        String deep = """
                class Deep {
                    void f(int[] xs) {
                        for (int x : xs) {
                            if (x > 0) {
                                while (x > 1) {
                                    try {
                                        x--;
                                    } finally {
                                        x++;
                                    }
                                }
                            }
                        }
                    }
                }
                """;

        assertThat(scan(flat, "Flat")).isEmpty();
        assertThat(scan(deep, "Deep")).singleElement().satisfies(s -> {
            assertThat(s.kind()).isEqualTo("deep nesting");
            assertThat(s.detail()).isEqualTo("f() nests control flow 4 levels deep");
            assertThat(s.fixScope()).isEqualTo(new LineRange(2, 14));
        });
    }

    @Test
    void findsPrintStackTraceAndIgnoresInterfaceConstants() {
        String source = """
                interface Settings {
                    int LIMIT = 3;
                }
                class Worker {
                    void work() {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                """;

        assertThat(scan(source, "Settings")).isEmpty();
        assertThat(scan(source, "Worker")).singleElement().satisfies(s -> {
            assertThat(s.kind()).isEqualTo("console error handling");
            assertThat(s.fixScope()).isEqualTo(new LineRange(9, 9));
        });
    }

    @Test
    void reportsALargeClass() {
        String source = "class Huge {\n" + "    int f;\n".repeat(CodeSmellScanner.LARGE_CLASS_LINES) + "}\n";

        assertThat(scan(source, "Huge")).extracting(ParsedClass.Suspect::kind).containsExactly("large class");
    }

    private static LineRange fixScope(List<ParsedClass.Suspect> suspects, String kind) {
        return suspects.stream().filter(s -> s.kind().equals(kind)).findFirst().orElseThrow().fixScope();
    }
}
