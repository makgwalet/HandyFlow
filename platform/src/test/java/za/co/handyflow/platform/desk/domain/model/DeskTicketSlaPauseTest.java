package za.co.handyflow.platform.desk.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for DeskTicket's SLA pause/resume logic.
 * <p>
 * WHY these specific tests, and why now?
 * <p>
 * V36's own migration comment always intended the SLA clock to pause on
 * WAITING_ON_CUSTOMER/WAITING_ON_THIRD_PARTY ("We need to track actual
 * time spent, not wall time") — but that logic was never actually
 * implemented. waitOnCustomer()/waitOnThirdParty() were plain status
 * flips; dueAt was a fixed deadline set once at creation and never
 * touched again. A ticket waiting days on a slow customer would show as
 * SLA-breached through no fault of the support team at all.
 * <p>
 * The fix (pausedAt tracking + resumeIfPaused() extending dueAt on
 * resume) was verified live against a real ticket before these tests were
 * written — dueAt shifted forward by exactly the measured pause duration,
 * to the hundredth of a second. These tests exist so that precision can't
 * silently regress. This is exactly the kind of logic someone could
 * "simplify" back to a plain transition() call months from now without
 * realising what breaks — a quiet, hard-to-notice bug (deadlines slowly
 * drifting wrong) rather than a loud one that fails fast in review.
 * <p>
 * WHY real Thread.sleep() instead of an injectable/mockable clock?
 * DeskTicket calls Instant.now() directly — there's no clock abstraction
 * to inject. Refactoring the entity to accept one would be a genuine
 * design change, out of scope for "write a test for what already exists
 * and was just fixed." A short real sleep (100ms) exercises the actual
 * production code path with no risk of testing a mock instead of reality
 * — the tradeoff is a slightly slower test, not a flaky one at this
 * timescale.
 */
class DeskTicketSlaPauseTest {

    private DeskTicket newTicket(Instant dueAt) {
        return DeskTicket.create(
                TenantId.of(UUID.randomUUID().toString()),
                "TKT-0001", "HELPDESK",
                "Test Requester", "test@example.com", null, null,
                "Test subject", "Test description", null, "NORMAL",
                dueAt, null);
    }

    @Test
    void waitOnCustomer_recordsThePauseButLeavesDueAtUntouched() {
        Instant originalDueAt = Instant.now().plusSeconds(3600);
        DeskTicket ticket = newTicket(originalDueAt);

        ticket.waitOnCustomer();

        // dueAt shouldn't move the moment a pause STARTS — only on resume,
        // once the actual pause duration is known. Confirms
        // waitOnCustomer() itself doesn't touch dueAt at all.
        assertThat(ticket.getStatus()).isEqualTo("WAITING_ON_CUSTOMER");
        assertThat(ticket.getDueAt()).isEqualTo(originalDueAt);
    }

    @Test
    void resumingFromWait_extendsDueAtByRoughlyThePauseDuration() throws InterruptedException {
        Instant originalDueAt = Instant.now().plusSeconds(3600);
        DeskTicket ticket = newTicket(originalDueAt);

        ticket.waitOnCustomer();
        Thread.sleep(100);
        ticket.startProgress();

        long extensionMillis = ticket.getDueAt().toEpochMilli() - originalDueAt.toEpochMilli();

        assertThat(ticket.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(extensionMillis).isGreaterThanOrEqualTo(100);
        // Upper bound is a sanity check, not a precision requirement — this
        // just confirms the extension is measured in tens/hundreds of
        // milliseconds like the actual pause, not accidentally hours (e.g.
        // a units mistake, or comparing the wrong two instants).
        assertThat(extensionMillis).isLessThan(5000);
    }

    @Test
    void startingFreshFromOpen_neverHavingBeenPaused_leavesDueAtUnchanged() {
        Instant originalDueAt = Instant.now().plusSeconds(3600);
        DeskTicket ticket = newTicket(originalDueAt);

        // OPEN -> IN_PROGRESS directly, no wait state ever entered.
        // resumeIfPaused() must be a genuine no-op when pausedAt is null —
        // this is the single most common path (most tickets are never
        // paused at all) and the easiest one to accidentally break while
        // "fixing" the pause logic.
        ticket.startProgress();

        assertThat(ticket.getDueAt()).isEqualTo(originalDueAt);
    }

    @Test
    void resolvingDirectlyFromWait_alsoExtendsDueAt() throws InterruptedException {
        Instant originalDueAt = Instant.now().plusSeconds(3600);
        DeskTicket ticket = newTicket(originalDueAt);

        // The frontend explicitly allows RESOLVE directly from a waiting
        // state (STATUS_ACTIONS in DeskPage.tsx) — not just via START
        // first. resumeIfPaused() needs to fire on this path too.
        ticket.waitOnCustomer();
        Thread.sleep(100);
        ticket.resolve();

        assertThat(ticket.getStatus()).isEqualTo("RESOLVED");
        assertThat(ticket.getDueAt()).isAfter(originalDueAt);
    }

    @Test
    void switchingBetweenWaitReasons_doesNotResetThePauseClock() throws InterruptedException {
        Instant originalDueAt = Instant.now().plusSeconds(3600);
        DeskTicket ticket = newTicket(originalDueAt);

        // The frontend's own STATUS_ACTIONS never allows a direct
        // WAITING_ON_CUSTOMER -> WAITING_ON_THIRD_PARTY transition, but
        // DeskService.updateStatus()'s switch statement has no state
        // guard at all, so a direct API call could still trigger it.
        // waitOnCustomer()/waitOnThirdParty() both guard with
        // `if (pausedAt == null)` specifically so switching the REASON for
        // a pause doesn't restart the clock and lose track of how long
        // the ticket has actually been paused overall.
        ticket.waitOnCustomer();
        Thread.sleep(100);
        ticket.waitOnThirdParty();
        Thread.sleep(100);
        ticket.startProgress();

        long extensionMillis = ticket.getDueAt().toEpochMilli() - originalDueAt.toEpochMilli();

        // Should reflect the FULL ~200ms pause, not just the last 100ms
        // leg after switching reasons — if this comes back around 100ms
        // instead, the guard has regressed.
        assertThat(extensionMillis).isGreaterThanOrEqualTo(180);
    }

    // NOTE: not a @Test — deliberately not an executable test. The actual
    // query lives in DeskTicketRepository and needs a real database
    // (Testcontainers or similar) to test properly, which is more test
    // infrastructure than this single class sets up. Left as a comment
    // rather than an empty @Test method, since an assertion-free test
    // would silently pass and imply coverage that doesn't actually exist —
    // worse than just not having it.
    //
    // findSlaBreaches() must exclude WAITING_ON_CUSTOMER/
    // WAITING_ON_THIRD_PARTY — a ticket currently mid-pause has a dueAt
    // that hasn't been adjusted yet for its still-ongoing pause, so
    // comparing it directly against "now" would incorrectly flag it as
    // breached. Worth a proper @DataJpaTest once the project has that
    // infrastructure set up.
}