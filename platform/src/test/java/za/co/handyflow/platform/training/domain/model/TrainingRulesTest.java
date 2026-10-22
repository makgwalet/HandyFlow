package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void onlyActiveEmployeesCanBePutOnASessionThatIsStillOpen() {
        assertThat(TrainingRules.enrollProblem("ACTIVE", "SCHEDULED")).isNull();
        assertThat(TrainingRules.enrollProblem("ACTIVE", "IN_PROGRESS")).isNull();
        assertThat(TrainingRules.enrollProblem("TERMINATED", "SCHEDULED")).contains("terminated");
        assertThat(TrainingRules.enrollProblem("SUSPENDED", "SCHEDULED")).contains("suspended");
        assertThat(TrainingRules.enrollProblem("ACTIVE", "CANCELLED")).contains("no longer accepts");
        assertThat(TrainingRules.enrollProblem("ACTIVE", "COMPLETED")).contains("no longer accepts");
    }

    @Test
    void attendanceNeedsAnEnrolledPersonAndASessionThatHasStartedOrWasStarted() {
        assertThat(TrainingRules.attendanceProblem("ENROLLED", "IN_PROGRESS", TODAY.plusDays(1), TODAY)).isNull();
        assertThat(TrainingRules.attendanceProblem("ENROLLED", "SCHEDULED", TODAY, TODAY)).isNull();
        assertThat(TrainingRules.attendanceProblem("ENROLLED", "SCHEDULED", TODAY.plusDays(2), TODAY)).contains("has not started");
        assertThat(TrainingRules.attendanceProblem("ENROLLED", "CANCELLED", TODAY, TODAY)).contains("cancelled");
        assertThat(TrainingRules.attendanceProblem("NO_SHOW", "IN_PROGRESS", TODAY, TODAY)).contains("still enrolled");
    }

    @Test
    void aResultCannotBeRecordedForANoShowOrACancelledBooking() {
        assertThat(TrainingRules.resultProblem("ATTENDED", "IN_PROGRESS", TODAY, TODAY, new BigDecimal("80"))).isNull();
        assertThat(TrainingRules.resultProblem("ENROLLED", "COMPLETED", TODAY.minusDays(3), TODAY, null)).isNull();
        assertThat(TrainingRules.resultProblem("NO_SHOW", "IN_PROGRESS", TODAY, TODAY, null)).contains("enrolled or has attended");
        assertThat(TrainingRules.resultProblem("CANCELLED", "IN_PROGRESS", TODAY, TODAY, null)).contains("enrolled or has attended");
        assertThat(TrainingRules.resultProblem("ATTENDED", "SCHEDULED", TODAY.plusDays(5), TODAY, null)).contains("has not started");
    }

    @Test
    void aScoreMustBeBetweenZeroAndAHundred() {
        assertThat(TrainingRules.resultProblem("ATTENDED", "IN_PROGRESS", TODAY, TODAY, new BigDecimal("100"))).isNull();
        assertThat(TrainingRules.resultProblem("ATTENDED", "IN_PROGRESS", TODAY, TODAY, BigDecimal.ZERO)).isNull();
        assertThat(TrainingRules.resultProblem("ATTENDED", "IN_PROGRESS", TODAY, TODAY, new BigDecimal("100.5"))).contains("between 0 and 100");
        assertThat(TrainingRules.resultProblem("ATTENDED", "IN_PROGRESS", TODAY, TODAY, new BigDecimal("-1"))).contains("between 0 and 100");
    }

    @Test
    void aSessionCannotBeCompletedWhileSomeoneIsStillShownAsEnrolled() {
        assertThat(TrainingRules.sessionCompleteProblem(0)).isNull();
        assertThat(TrainingRules.sessionCompleteProblem(1)).contains("1 person");
        assertThat(TrainingRules.sessionCompleteProblem(3)).contains("3 people");
    }

    @Test
    void capacityCannotBeSetBelowThePeopleAlreadyOnTheSession() {
        assertThat(TrainingRules.capacityProblem(null, 5)).isNull();
        assertThat(TrainingRules.capacityProblem(5, 5)).isNull();
        assertThat(TrainingRules.capacityProblem(4, 5)).contains("already 5");
        assertThat(TrainingRules.capacityProblem(0, 0)).contains("at least 1");
    }

    @Test
    void messagesNameTheCourseAndDate() {
        assertThat(TrainingRules.enrolledMessage("First Aid", TODAY, "Head Office")).contains("First Aid").contains("2026-10-22").contains("Head Office");
        assertThat(TrainingRules.enrolledMessage(null, TODAY, null)).contains("a training session").endsWith("2026-10-22.");
        assertThat(TrainingRules.movedMessage("First Aid", TODAY, TODAY.plusDays(1))).contains("2026-10-23");
        assertThat(TrainingRules.cancelledMessage("First Aid", TODAY)).contains("cancelled");
    }
}
