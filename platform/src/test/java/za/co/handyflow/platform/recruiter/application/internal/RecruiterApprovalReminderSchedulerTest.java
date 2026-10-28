package za.co.handyflow.platform.recruiter.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterApprovalReminderSchedulerTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private final LocalDate today = LocalDate.of(2026, 10, 28);

    private RecruiterApprovalReminderScheduler.Waiting since(LocalDate day, boolean job) {
        return new RecruiterApprovalReminderScheduler.Waiting(UUID.randomUUID(), job, UUID.randomUUID(), "Site Clerk", UUID.randomUUID(),
                day == null ? null : day.atTime(15, 0).atZone(SAST).toInstant());
    }

    @Test
    void onlyRequestsAtTheirReminderDayAreDue() {
        var items = List.of(since(today, true), since(today.minusDays(1), true), since(today.minusDays(2), true),
                since(today.minusDays(4), false), since(today.minusDays(5), false), since(today.minusDays(8), true));
        var due = RecruiterApprovalReminderScheduler.dueIndexes(items, today);
        assertThat(due.size()).isEqualTo(3);
        assertThat(due.get(0)[0]).isEqualTo(2L);
        assertThat(due.get(0)[1]).isEqualTo(2L);
        assertThat(due.get(1)[0]).isEqualTo(4L);
        assertThat(due.get(2)[0]).isEqualTo(5L);
        assertThat(due.get(2)[1]).isEqualTo(8L);
    }

    @Test
    void aRequestWithNoDateIsLeftAlone() {
        assertThat(RecruiterApprovalReminderScheduler.dueIndexes(List.of(since(null, true)), today).isEmpty()).isTrue();
    }
}
