package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.hr.domain.model.EmployeeStatusRules.*;

class EmployeeStatusRulesTest {

    private static final ZoneId SA = ZoneId.of("Africa/Johannesburg");
    private static final String WHY = "Safety incident on site, under investigation";

    @Test
    void anActiveEmployeeCanBeSuspendedAndASuspendedOneReinstated() {
        assertThat(problem("ACTIVE", "SUSPENDED", WHY, false)).isNull();
        assertThat(problem("SUSPENDED", "ACTIVE", WHY, false)).isNull();
    }

    @Test
    void nothingElseIsAStatusChange() {
        assertThat(problem("ACTIVE", "TERMINATED", WHY, false)).contains("termination");
        assertThat(problem("TERMINATED", "ACTIVE", WHY, false)).contains("suspended");
        assertThat(problem("TERMINATED", "SUSPENDED", WHY, false)).contains("active");
        assertThat(problem("ACTIVE", "ACTIVE", WHY, false)).contains("already");
        assertThat(problem("SUSPENDED", "SUSPENDED", WHY, false)).contains("already");
    }

    @Test
    void aReasonIsRequired() {
        assertThat(problem("ACTIVE", "SUSPENDED", null, false)).contains("reason");
        assertThat(problem("ACTIVE", "SUSPENDED", "short", false)).contains("reason");
        assertThat(problem("ACTIVE", "SUSPENDED", "x".repeat(1001), false)).contains("1000");
    }

    @Test
    void onlyASuspensionCanBeAnEmergency() {
        assertThat(problem("ACTIVE", "SUSPENDED", WHY, true)).isNull();
        assertThat(problem("SUSPENDED", "ACTIVE", WHY, true)).contains("emergency");
    }

    @Test
    void theReviewIsDueByTheEndOfTheNextBusinessDay() {
        Instant tuesday = ZonedDateTime.of(2026, 11, 3, 10, 0, 0, 0, SA).toInstant();
        assertThat(reviewDueAt(tuesday).atZone(SA)).isEqualTo(ZonedDateTime.of(2026, 11, 4, 23, 59, 0, 0, SA));
    }

    @Test
    void aFridayOrWeekendSuspensionIsDueOnTheFollowingMonday() {
        for (int day : new int[] {6, 7, 8}) {   // Fri 6 Nov, Sat 7 Nov, Sun 8 Nov 2026
            Instant at = ZonedDateTime.of(2026, 11, day, 15, 0, 0, 0, SA).toInstant();
            assertThat(reviewDueAt(at).atZone(SA).toLocalDate()).as("day " + day).hasToString("2026-11-09");
        }
    }

    @Test
    void overdueOnlyAfterTheDeadline() {
        Instant due = ZonedDateTime.of(2026, 11, 4, 23, 59, 0, 0, SA).toInstant();
        assertThat(reviewOverdue(due, due.minusSeconds(1))).isFalse();
        assertThat(reviewOverdue(due, due.plusSeconds(1))).isTrue();
        assertThat(reviewOverdue(null, Instant.now())).isFalse();
    }
}
