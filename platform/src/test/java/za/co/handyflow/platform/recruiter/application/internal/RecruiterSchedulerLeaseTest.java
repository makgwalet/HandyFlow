package za.co.handyflow.platform.recruiter.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** With several app instances only the one that gets the lease runs a reminder sweep; the others skip it without touching the data. */
class RecruiterSchedulerLeaseTest {

    private final List<String> locks = new ArrayList<>();

    private SchedulerLease declining() {
        return new SchedulerLease(null) {
            @Override public boolean runExclusively(String lockName, Duration maxHold, Runnable task) { locks.add(lockName); return false; }
        };
    }

    @Test
    void theApprovalReminderSweepWaitsForTheLease() {
        // No database here: had the sweep run, it would have failed on the missing connection.
        new RecruiterApprovalReminderScheduler(null, null, declining()).remindApprovers();
        assertThat(locks).containsExactly("recruiter-approval-reminders");
    }

    @Test
    void theInterviewReminderSweepWaitsForTheLease() {
        new InterviewReminderScheduler(null, null, null, null, null, null, null, null, declining()).sendUpcomingInterviewReminders();
        assertThat(locks).containsExactly("recruiter-interview-reminders");
    }

    @Test
    void theTwoSweepsUseDifferentLocks() {
        assertThat(RecruiterApprovalReminderScheduler.LOCK).isNotEqualTo(InterviewReminderScheduler.LOCK);
    }
}
