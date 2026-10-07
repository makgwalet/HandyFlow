package za.co.handyflow.platform.clinic.domain.question;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerValidatorTest {

    static QuestionDef of(AnswerType t) { return QuestionDef.of("q", t); }

    @Test
    @DisplayName("selects accept only listed options")
    void selects() {
        QuestionDef sel = of(AnswerType.SINGLE_SELECT).withOptions("A", "B");
        assertThat(AnswerValidator.problem(sel, "A")).isNull();
        assertThat(AnswerValidator.problem(sel, "Z")).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.MULTI_SELECT).withOptions("A"), List.of("A", "Q"))).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.CHECKLIST).withOptions("A", "B"), List.of("A", "B"))).isNull();
    }

    @Test
    @DisplayName("numbers: NUMBER is whole, DECIMAL is not, both honour min and max")
    void numbers() {
        assertThat(AnswerValidator.problem(of(AnswerType.NUMBER), 3.5)).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.NUMBER), 3)).isNull();
        assertThat(AnswerValidator.problem(of(AnswerType.NUMBER).withRange(0, 365), 400)).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.DECIMAL), "36.6")).isNull();
        assertThat(AnswerValidator.problem(of(AnswerType.DECIMAL), "abc")).isNotNull();
    }

    @Test
    @DisplayName("booleans, dates and text")
    void basics() {
        assertThat(AnswerValidator.problem(of(AnswerType.YES_NO), "yes")).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.TOGGLE), true)).isNull();
        assertThat(AnswerValidator.problem(of(AnswerType.DATE), "2026-10-07")).isNull();
        assertThat(AnswerValidator.problem(of(AnswerType.DATE), "07/10/2026")).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.DATE_TIME), "2026-10-07T10:15:00")).isNull();
        assertThat(AnswerValidator.problem(of(AnswerType.TEXT), "x".repeat(501))).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.LONG_TEXT), "x".repeat(501))).isNull();
    }

    @Test
    @DisplayName("durations, measurements, scales and body regions")
    void structured() {
        assertThat(AnswerValidator.problem(of(AnswerType.DURATION), Map.of("value", 3, "unit", "DAYS"))).isNull();
        assertThat(AnswerValidator.problem(of(AnswerType.DURATION), Map.of("value", 3, "unit", "FORTNIGHTS"))).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.MEASUREMENT), Map.of("value", 70))).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.MEASUREMENT), Map.of("value", 70, "unit", "kg"))).isNull();
        assertThat(AnswerValidator.problem(of(AnswerType.SCALE), 11)).isNotNull();
        assertThat(AnswerValidator.problem(of(AnswerType.SCALE), 7)).isNull();
        assertThat(AnswerValidator.problem(of(AnswerType.BODY), List.of("CHEST", "LEFT_ARM"))).isNull();
    }

    @Test
    @DisplayName("an empty answer is always shape-valid (required is the engine's job)")
    void emptyIsFine() {
        QuestionDef sel = of(AnswerType.SINGLE_SELECT).withOptions("A");
        assertThat(AnswerValidator.problem(sel, "")).isNull();
        assertThat(AnswerValidator.problem(sel, null)).isNull();
    }
}
