package dev.exorcist.code;

import dev.exorcist.ExorcistException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineRangeTest {

    @Test
    void parsesARangeOrASingleLine() {
        assertThat(LineRange.parse("40-52")).isEqualTo(new LineRange(40, 52));
        assertThat(LineRange.parse(" 40 - 52 ")).isEqualTo(new LineRange(40, 52));
        assertThat(LineRange.parse("7")).isEqualTo(new LineRange(7, 7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "forty", "40-", "40..52", "0-3", "52-40"})
    void rejectsWhatIsNotARange(String text) {
        assertThatThrownBy(() -> LineRange.parse(text)).isInstanceOf(ExorcistException.class);
    }

    @Test
    void describesItself() {
        assertThat(new LineRange(40, 52)).hasToString("lines 40-52");
        assertThat(new LineRange(7, 7)).hasToString("line 7");
        assertThat(new LineRange(40, 52).size()).isEqualTo(13);
    }

    @Test
    void cannotBeBuiltBackwards() {
        assertThatThrownBy(() -> new LineRange(5, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
