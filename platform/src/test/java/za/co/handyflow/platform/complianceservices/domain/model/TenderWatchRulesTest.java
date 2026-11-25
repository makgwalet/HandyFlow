package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenderWatchRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    static final UUID C = UUID.randomUUID();

    private WorkQueueRules.Item closing(String status, LocalDate d, int missing, int expired) {
        return TenderWatchRules.closing("Roads", C, "Acme", status, d, TODAY, missing, expired);
    }

    @Test
    void closingSoonIsMediumAndWithinThreeDaysHigh() {
        assertThat(closing("IN_PREPARATION", TODAY.plusDays(10), 0, 0).severity().equals(WorkQueueRules.MEDIUM)).isTrue();
        assertThat(closing("IN_PREPARATION", TODAY.plusDays(2), 0, 0).severity().equals(WorkQueueRules.HIGH)).isTrue();
    }

    @Test
    void passedWithoutSubmissionIsHighAndSaysSo() {
        WorkQueueRules.Item i = closing("DRAFT", TODAY.minusDays(1), 0, 0);
        assertThat(i.severity().equals(WorkQueueRules.HIGH)).isTrue();
        assertThat(i.detail().contains("not been submitted")).isTrue();
    }

    @Test
    void submittedOrFarOffTendersAreQuiet() {
        assertThat(closing("SUBMITTED", TODAY.plusDays(1), 3, 0) == null).isTrue();
        assertThat(closing("AWARDED", TODAY.minusDays(5), 0, 0) == null).isTrue();
        assertThat(closing("DRAFT", TODAY.plusDays(60), 5, 0) == null).isTrue();
        assertThat(closing("DRAFT", null, 5, 0) == null).isTrue();
    }

    @Test
    void gapsBringALaterTenderIntoTheQueueAsLow() {
        WorkQueueRules.Item i = closing("DRAFT", TODAY.plusDays(20), 2, 1);
        assertThat(i.severity().equals(WorkQueueRules.LOW)).isTrue();
        assertThat(i.detail().contains("2 requirements missing, 1 expired")).isTrue();
        assertThat(closing("DRAFT", TODAY.plusDays(20), 0, 0) == null).isTrue();
    }

    @Test
    void gapTextIsPlain() {
        assertThat(TenderWatchRules.gapText(1, 0).equals("1 requirement missing")).isTrue();
        assertThat(TenderWatchRules.gapText(0, 2).equals("2 expired")).isTrue();
    }

    @Test
    void eventsOnlyInTheNextWeekWhilePreparing() {
        assertThat(TenderWatchRules.event("T", C, "A", "DRAFT", "The briefing", TODAY.plusDays(1), TODAY).severity().equals(WorkQueueRules.MEDIUM)).isTrue();
        assertThat(TenderWatchRules.event("T", C, "A", "DRAFT", "The briefing", TODAY.plusDays(6), TODAY).severity().equals(WorkQueueRules.LOW)).isTrue();
        assertThat(TenderWatchRules.event("T", C, "A", "DRAFT", "The briefing", TODAY.plusDays(8), TODAY) == null).isTrue();
        assertThat(TenderWatchRules.event("T", C, "A", "DRAFT", "The briefing", TODAY.minusDays(1), TODAY) == null).isTrue();
        assertThat(TenderWatchRules.event("T", C, "A", "SUBMITTED", "The briefing", TODAY.plusDays(1), TODAY) == null).isTrue();
    }

    @Test
    void readinessOnlyCheckedWhenWorthIt() {
        assertThat(TenderWatchRules.worthChecking("DRAFT", TODAY.plusDays(30), TODAY)).isTrue();
        assertThat(TenderWatchRules.worthChecking("DRAFT", TODAY.plusDays(31), TODAY)).isFalse();
        assertThat(TenderWatchRules.worthChecking("SUBMITTED", TODAY, TODAY)).isFalse();
    }
}
