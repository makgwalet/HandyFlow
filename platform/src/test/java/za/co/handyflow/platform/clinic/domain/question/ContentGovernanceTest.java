package za.co.handyflow.platform.clinic.domain.question;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentGovernanceTest {

    final UUID reviewer = UUID.randomUUID();
    final UUID other = UUID.randomUUID();

    @Test
    @DisplayName("review needs a recorded source and valid rules")
    void reviewPreconditions() {
        assertThatThrownBy(() -> ContentGovernance.checkTransition(ContentStatus.DRAFT, ContentStatus.CLINICAL_REVIEW, null, other, false, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source");
        assertThatThrownBy(() -> ContentGovernance.checkTransition(ContentStatus.DRAFT, ContentStatus.CLINICAL_REVIEW, null, other, false, List.of("bad"), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> ContentGovernance.checkTransition(ContentStatus.DRAFT, ContentStatus.CLINICAL_REVIEW, null, other, false, List.of(), true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("activation is four-eyes: the reviewer cannot activate, and a reviewer must exist")
    void fourEyes() {
        assertThatThrownBy(() -> ContentGovernance.checkTransition(ContentStatus.APPROVED, ContentStatus.ACTIVE, reviewer, reviewer, false, List.of(), true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("different person");
        assertThatThrownBy(() -> ContentGovernance.checkTransition(ContentStatus.APPROVED, ContentStatus.ACTIVE, null, other, false, List.of(), true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reviewed");
        assertThatCode(() -> ContentGovernance.checkTransition(ContentStatus.APPROVED, ContentStatus.ACTIVE, reviewer, other, false, List.of(), true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("synthetic demo content can never become ACTIVE, and states cannot be skipped")
    void demoAndSkips() {
        assertThatThrownBy(() -> ContentGovernance.checkTransition(ContentStatus.APPROVED, ContentStatus.ACTIVE, reviewer, other, true, List.of(), true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("demo");
        assertThatThrownBy(() -> ContentGovernance.checkTransition(ContentStatus.DRAFT, ContentStatus.ACTIVE, reviewer, other, false, List.of(), true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("form data keeps other groups when one group's answers are replaced")
    void formDataMerge() {
        Map<String, Object> fd = FormData.withGroup(null, "G1", 1, Map.of("a", 1));
        fd = FormData.withGroup(fd, "G2", 2, Map.of("b", 2));
        fd = FormData.withGroup(fd, "G1", 1, Map.of("a", 5));

        assertThat(FormData.answersOf(fd, "G1")).containsEntry("a", 5);
        assertThat(FormData.answersOf(fd, "G2")).containsEntry("b", 2);
        assertThat(FormData.answersOf(fd, "NOPE")).isEmpty();
        assertThat(FormData.answersOf(null, "X")).isEmpty();
    }

    @Test
    @DisplayName("age in whole months, unknown when there is no date of birth")
    void ageMonths() {
        assertThat(FormData.ageMonths(LocalDate.of(2020, 1, 15), LocalDate.of(2021, 3, 14))).isEqualTo(13);
        assertThat(FormData.ageMonths(null, LocalDate.now())).isNull();
    }
}
