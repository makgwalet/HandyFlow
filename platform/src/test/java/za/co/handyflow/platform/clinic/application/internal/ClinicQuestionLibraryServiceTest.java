package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.ClinicQuestionLibraryService.GroupRow;
import za.co.handyflow.platform.clinic.application.internal.ClinicQuestionLibraryService.PatientFacts;
import za.co.handyflow.platform.clinic.dto.QuestionLibraryDtos.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the parts that need no database: applicability filtering and evaluation/validation of answers. */
class ClinicQuestionLibraryServiceTest {

    final ClinicQuestionLibraryService service =
            new ClinicQuestionLibraryService(null, new ObjectMapper(), null, false);

    static GroupRow row(Integer minAge, Integer maxAge, List<String> sex, List<String> visitTypes) {
        return new GroupRow(UUID.randomUUID(), null, "G", 1, "G", null, minAge, maxAge, sex, visitTypes, true, "ACTIVE", false);
    }

    @Test
    @DisplayName("a group with no restrictions applies to everyone")
    void unrestricted() {
        assertThat(ClinicQuestionLibraryService.applicable(row(null, null, null, null), null, new PatientFacts(null, null, null))).isTrue();
    }

    @Test
    @DisplayName("restrictions never match unknown facts: unknown sex or age hides a restricted group")
    void unknownNeverMatches() {
        PatientFacts unknown = new PatientFacts(null, null, null);
        assertThat(ClinicQuestionLibraryService.applicable(row(null, null, List.of("FEMALE"), null), "CONSULTATION", unknown)).isFalse();
        assertThat(ClinicQuestionLibraryService.applicable(row(144, null, null, null), "CONSULTATION", unknown)).isFalse();
        assertThat(ClinicQuestionLibraryService.applicable(row(null, null, null, List.of("CONSULTATION")), null, unknown)).isFalse();
    }

    @Test
    @DisplayName("sex, age band and visit type are all honoured when known")
    void restrictionsApply() {
        GroupRow g = row(144, 600, List.of("FEMALE"), List.of("CONSULTATION"));
        assertThat(ClinicQuestionLibraryService.applicable(g, "CONSULTATION", new PatientFacts(360, "FEMALE", null))).isTrue();
        assertThat(ClinicQuestionLibraryService.applicable(g, "CONSULTATION", new PatientFacts(360, "MALE", null))).isFalse();
        assertThat(ClinicQuestionLibraryService.applicable(g, "CONSULTATION", new PatientFacts(60, "FEMALE", null))).isFalse();
        assertThat(ClinicQuestionLibraryService.applicable(g, "CONSULTATION", new PatientFacts(700, "FEMALE", null))).isFalse();
        assertThat(ClinicQuestionLibraryService.applicable(g, "FOLLOW_UP", new PatientFacts(360, "FEMALE", null))).isFalse();
    }

    private GroupView demoGroup() {
        RuleView showIll = new RuleView("SHOW_WHEN", Map.of("q", "reason", "op", "EQ", "value", "ILLNESS"), null, null);
        QuestionView reason = new QuestionView("reason", "Reason", null, "SINGLE_SELECT",
                List.of(Map.of("value", "ROUTINE", "label", "Routine"), Map.of("value", "ILLNESS", "label", "Unwell")),
                null, null, true, true, null, List.of());
        QuestionView score = new QuestionView("score", "Score", null, "SCALE", List.of(),
                BigDecimal.ZERO, BigDecimal.TEN, false, false, null,
                List.of(showIll, new RuleView("WARNING_WHEN", Map.of("q", "score", "op", "GTE", "value", 8), "High", null)));
        RedFlagView flag = new RedFlagView("TOP", "Top", "URGENT", Map.of("q", "score", "op", "GTE", "value", 10), "Look at this");
        return new GroupView(UUID.randomUUID(), "DEMO", 1, "Demo", null, false, true, "DRAFT", true,
                List.of(reason, score), List.of(flag));
    }

    @Test
    @DisplayName("evaluation reveals, warns, flags and reports missing required answers")
    void evaluates() {
        PatientFacts none = new PatientFacts(null, null, null);

        var empty = service.evaluateView(demoGroup(), Map.of(), none, null);
        assertThat(empty.visible()).containsExactly("reason");
        assertThat(empty.missingRequired()).containsExactly("reason");

        var ill = service.evaluateView(demoGroup(), Map.of("reason", "ILLNESS", "score", 10), none, null);
        assertThat(ill.visible()).containsExactlyInAnyOrder("reason", "score");
        assertThat(ill.warnings()).containsKey("score");
        assertThat(ill.urgent()).isTrue();
        assertThat(ill.problems()).isEmpty();
    }

    @Test
    @DisplayName("invalid answers are reported per question, and answers to hidden questions are dropped, not flagged")
    void validatesVisibleOnly() {
        PatientFacts none = new PatientFacts(null, null, null);

        var bad = service.evaluateView(demoGroup(), Map.of("reason", "ILLNESS", "score", 11), none, null);
        assertThat(bad.problems()).containsKey("score");

        var hidden = service.evaluateView(demoGroup(), Map.of("reason", "ROUTINE", "score", 99), none, null);
        assertThat(hidden.problems()).isEmpty();
        assertThat(hidden.effectiveAnswers()).doesNotContainKey("score");
        assertThat(hidden.urgent()).isFalse();
    }
}
