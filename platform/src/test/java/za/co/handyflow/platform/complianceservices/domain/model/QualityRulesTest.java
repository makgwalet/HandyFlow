package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityRulesTest {

    private static QualityRules.Line line(int p, boolean critical, String result) { return new QualityRules.Line(p, critical, result); }

    @Test @DisplayName("what is being reviewed and each check's result must be one of the known values")
    void vocabulary() {
        assertThat(QualityRules.subjectType(" case ")).isEqualTo("CASE");
        assertThatThrownBy(() -> QualityRules.subjectType("invoice")).isInstanceOf(IllegalArgumentException.class);
        assertThat(QualityRules.result("fail")).isEqualTo("FAIL");
        assertThatThrownBy(() -> QualityRules.result("maybe")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("a failed check, or one that does not apply, says why; a pass needs no note")
    void itemNotes() {
        assertThat(QualityRules.itemNote("PASS", null)).isNull();
        assertThat(QualityRules.itemNote("PASS", " looks fine ")).isEqualTo("looks fine");
        assertThatThrownBy(() -> QualityRules.itemNote("FAIL", "bad")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QualityRules.itemNote("NA", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(QualityRules.itemNote("FAIL", "The VAT number is wrong")).isEqualTo("The VAT number is wrong");
    }

    @Test @DisplayName("approval needs every check marked and no critical failure")
    void approval() {
        assertThat(QualityRules.completionProblem("APPROVED", List.of(line(1, false, "PASS"), line(2, true, "NA")))).isNull();
        assertThat(QualityRules.completionProblem("APPROVED", List.of(line(1, false, "PASS"), line(2, false, "PENDING")))).contains("Mark every check");
        assertThat(QualityRules.completionProblem("APPROVED", List.of(line(1, true, "FAIL"), line(2, false, "PASS")))).contains("critical");
        assertThat(QualityRules.completionProblem("APPROVED", List.of(line(1, false, "FAIL"), line(2, false, "PASS")))).isNull();
    }

    @Test @DisplayName("sending work back needs at least one failed check, and an unknown outcome is refused")
    void rework() {
        assertThat(QualityRules.completionProblem("REWORK_REQUIRED", List.of(line(1, false, "FAIL"), line(2, false, "PENDING")))).isNull();
        assertThat(QualityRules.completionProblem("REWORK_REQUIRED", List.of(line(1, false, "PASS")))).contains("at least one check");
        assertThat(QualityRules.completionProblem("DONE", List.of())).isNotNull();
    }

    @Test @DisplayName("the person who prepared the work needs a reason of 15 characters to review it")
    void separation() {
        UUID preparer = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertThat(QualityRules.exception(preparer, other, null)).isNull();
        assertThat(QualityRules.exception(null, preparer, null)).isNull();
        assertThatThrownBy(() -> QualityRules.exception(preparer, preparer, "short")).isInstanceOf(IllegalArgumentException.class);
        assertThat(QualityRules.exception(preparer, preparer, "  Only reviewer on leave cover  ")).isEqualTo("Only reviewer on leave cover");
    }

    @Test @DisplayName("a sample is the rate rounded up, at least one, never more than the pool")
    void sampleSize() {
        assertThat(QualityRules.sampleSize(0, 10)).isEqualTo(0);
        assertThat(QualityRules.sampleSize(5, 0)).isEqualTo(0);
        assertThat(QualityRules.sampleSize(5, 1)).isEqualTo(1);
        assertThat(QualityRules.sampleSize(25, 10)).isEqualTo(3);
        assertThat(QualityRules.sampleSize(4, 100)).isEqualTo(4);
        assertThat(QualityRules.sampleSize(4, 500)).isEqualTo(4);
    }

    @Test @DisplayName("picking is random but repeatable for one seed, and picks distinct items")
    void pick() {
        List<Integer> pool = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Integer> a = QualityRules.pick(pool, 30, new Random(42));
        List<Integer> b = QualityRules.pick(pool, 30, new Random(42));
        assertThat(a.size()).isEqualTo(3);
        assertThat(a.equals(b)).isTrue();
        assertThat(a.stream().distinct().count()).isEqualTo(3L);
        assertThat(pool.containsAll(a)).isTrue();
        assertThat(QualityRules.pick(List.<Integer>of(), 30, new Random(1)).isEmpty()).isTrue();
    }

    @Test @DisplayName("the sample period and rate are checked")
    void checkSample() {
        LocalDate d = LocalDate.of(2026, 11, 1);
        QualityRules.checkSample(d, d.plusDays(30), 10);
        assertThatThrownBy(() -> QualityRules.checkSample(null, d, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QualityRules.checkSample(d, d.minusDays(1), 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QualityRules.checkSample(d, d.plusDays(400), 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QualityRules.checkSample(d, d.plusDays(1), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QualityRules.checkSample(d, d.plusDays(1), 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("an in-progress review is low until it is a week old; a review sent back with no next round is medium")
    void queue() {
        LocalDate today = LocalDate.of(2026, 11, 25);
        UUID c = UUID.randomUUID();
        Instant tenDays = today.minusDays(10).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        Instant now = today.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        assertThat(QualityRules.queueItem(today, c, "Acme", "Assessment", "IN_PROGRESS", now).severity()).isEqualTo("LOW");
        assertThat(QualityRules.queueItem(today, c, "Acme", "Assessment", "IN_PROGRESS", tenDays).severity()).isEqualTo("MEDIUM");
        var rework = QualityRules.queueItem(today, c, "Acme", "Assessment", "REWORK_REQUIRED", now);
        assertThat(rework.type()).isEqualTo("QUALITY_REWORK");
        assertThat(rework.severity()).isEqualTo("MEDIUM");
    }

    @Test @DisplayName("what can be done to a review depends on its state")
    void actions() {
        assertThat(QualityRules.actions("IN_PROGRESS", false)).containsExactly("RECORD", "COMPLETE");
        assertThat(QualityRules.actions("REWORK_REQUIRED", false)).containsExactly("NEXT_ROUND");
        assertThat(QualityRules.actions("REWORK_REQUIRED", true).isEmpty()).isTrue();
        assertThat(QualityRules.actions("APPROVED", false).isEmpty()).isTrue();
    }
}
