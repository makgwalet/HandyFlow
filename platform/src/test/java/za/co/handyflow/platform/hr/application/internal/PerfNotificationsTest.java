package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.dto.PerfDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.hr.application.internal.PerfHarness.*;

/** Who is told when it becomes their turn in a review, and that the words never carry a rating or a comment. */
class PerfNotificationsTest {

    private final PerfHarness h = new PerfHarness();
    private final UUID boss = h.employee("Bongani", "Boss");
    private final UUID hr = h.employee("Hanna", "Hart", boss);
    private final UUID ann = h.employee("Ann", "Adams", boss);
    private final UUID ben = h.employee("Ben", "Brown", boss);
    private final UUID hrUser = h.signedInAs(hr);
    private final UUID bossUser = h.signedInAs(boss);
    private final UUID annUser = h.signedInAs(ann);
    private final LocalDate today = today();

    private void email(UUID employee, String address) {
        lenient().when(h.staff.get(employee).getEmail()).thenReturn(address);
    }

    private CycleDetail openCycle(boolean feedback360) {
        CycleDetail c = h.cycles.create(TENANT, new CycleRequest("Mid-year 2026", today.minusDays(120), today.minusDays(10), today.plusDays(7), today.plusDays(14), feedback360), hrUser, HR);
        return h.cycles.launch(TENANT, c.row().id(), hrUser, HR);
    }

    private UUID reviewOf(CycleDetail c, UUID employee) {
        return c.reviews().stream().filter(r -> r.employeeId().equals(employee)).findFirst().orElseThrow().id();
    }

    private List<NotificationRequest> sent() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(h.notificationService, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    private static List<String> emails(NotificationRequest n) { return n.recipients().stream().map(Recipient::email).toList(); }

    @Test
    void openingACycleAsksEachEmployeeWithAnEmailForTheirSelfReview() {
        email(ann, "ann@example.com");
        email(ben, "ben@example.com");
        openCycle(false);
        List<NotificationRequest> out = sent().stream().filter(n -> n.type() == NotificationType.PERF_SELF_REVIEW_DUE).toList();
        assertThat(out.stream().flatMap(n -> emails(n).stream()).toList()).contains("ann@example.com", "ben@example.com");
        assertThat(out.get(0).message()).contains("Mid-year 2026").contains(today.plusDays(7).toString());
    }

    @Test
    void someoneWithoutAnEmailIsSkippedWithoutError() {
        openCycle(false);   // nobody has an email address
        assertThat(sent()).isEmpty();
    }

    @Test
    void submittingTheSelfReviewTellsTheReviewerWithoutSayingWhatWasWritten() {
        email(boss, "boss@example.com");
        CycleDetail c = openCycle(false);
        h.reviews.submitSelf(TENANT, reviewOf(c, ann), new SelfRequest(4, "Secret thoughts about my manager."), annUser, NONE);
        NotificationRequest n = sent().stream().filter(x -> x.type() == NotificationType.PERF_READY_FOR_MANAGER).findFirst().orElseThrow();
        assertThat(emails(n)).containsExactly("boss@example.com");
        assertThat(n.title()).contains("Ann Adams");
        assertThat(n.message()).doesNotContain("Secret").doesNotContain("4");
    }

    @Test
    void releasingTheResultTellsTheEmployee() {
        email(ann, "ann@example.com");
        CycleDetail c = openCycle(false);
        UUID id = reviewOf(c, ann);
        h.reviews.submitSelf(TENANT, id, new SelfRequest(4, "A good half-year."), annUser, NONE);
        h.reviews.submitManager(TENANT, id, new ManagerRequest(4, "Delivered the project on time and mentored two new starters."), bossUser, NONE);
        h.reviews.share(TENANT, id, hrUser, HR);
        NotificationRequest n = sent().stream().filter(x -> x.type() == NotificationType.PERF_RESULT_RELEASED).findFirst().orElseThrow();
        assertThat(emails(n)).containsExactly("ann@example.com");
        assertThat(n.message()).doesNotContain("mentored");
    }

    @Test
    void askingForFeedbackTellsEachProvider() {
        email(ben, "ben@example.com");
        CycleDetail c = openCycle(true);
        h.feedback.request(TENANT, reviewOf(c, ann), new FeedbackRequestBody(List.of(ben), "PEER"), bossUser, NONE);
        NotificationRequest n = sent().stream().filter(x -> x.type() == NotificationType.PERF_FEEDBACK_REQUESTED).findFirst().orElseThrow();
        assertThat(emails(n)).containsExactly("ben@example.com");
        assertThat(n.title()).contains("Ann Adams");
    }

    @Test
    void aMessageThatCannotBeSentNeverStopsTheReviewStep() {
        email(boss, "boss@example.com");
        doThrow(new IllegalStateException("mail down")).when(h.notificationService).send(any());
        CycleDetail c = openCycle(false);
        ReviewDetail d = h.reviews.submitSelf(TENANT, reviewOf(c, ann), new SelfRequest(4, "A good half-year."), annUser, NONE);
        assertThat(d).isNotNull();
        assertThat(h.reviewStore.stream().filter(r -> r.getEmployeeId().equals(ann)).findFirst().orElseThrow().getStatus()).isEqualTo(PerfRules.AWAITING_MANAGER);
    }

    // ── Daily reminders ─────────────────────────────────────────────────────────────────────────

    private PerfReminderService reminders(TenantAdminRecipients admins) {
        return new PerfReminderService(h.cycleRepo, h.reviewRepo, h.employees, h.notifier, admins);
    }

    @Test
    void anOverdueSelfReviewIsRemindedTheDayAfterAndSkippedTheDayAfterThat() {
        email(ann, "ann@example.com");
        HrPerfCycle cycle = HrPerfCycle.of(TENANT.getValue(), "Mid-year 2026", today.minusDays(120), today.minusDays(10), today.minusDays(1), today.plusDays(14), false, hrUser);
        cycle.launch(hrUser);
        HrPerfReview review = HrPerfReview.of(TENANT.getValue(), cycle.getId(), ann, boss, "Clerk");
        when(h.cycleRepo.allOpen()).thenReturn(List.of(cycle));
        when(h.reviewRepo.forCycles(any())).thenReturn(List.of(review));
        TenantAdminRecipients admins = mock(TenantAdminRecipients.class);

        assertThat(reminders(admins).remindAll(today)).isEqualTo(1);
        assertThat(sent().get(0).message()).contains("was due on").contains("1 day ago");
        assertThat(reminders(admins).remindAll(today.plusDays(1))).isZero();
        assertThat(reminders(admins).remindAll(today.plusDays(6))).isEqualTo(1);   // seven days after it fell due
    }

    @Test
    void reviewsPastTheManagerDateAreOneMessagePerReviewerAndTheAdministratorsHearWhenThereIsNoReviewer() {
        email(boss, "boss@example.com");
        HrPerfCycle cycle = HrPerfCycle.of(TENANT.getValue(), "Mid-year 2026", today.minusDays(120), today.minusDays(10), today.minusDays(5), today.minusDays(1), false, hrUser);
        cycle.launch(hrUser);
        HrPerfReview a = HrPerfReview.of(TENANT.getValue(), cycle.getId(), ann, boss, "Clerk");
        HrPerfReview b = HrPerfReview.of(TENANT.getValue(), cycle.getId(), ben, boss, "Clerk");
        HrPerfReview orphan = HrPerfReview.of(TENANT.getValue(), cycle.getId(), hr, null, "Clerk");
        when(h.cycleRepo.allOpen()).thenReturn(List.of(cycle));
        when(h.reviewRepo.forCycles(any())).thenReturn(List.of(a, b, orphan));
        TenantAdminRecipients admins = mock(TenantAdminRecipients.class);
        when(admins.resolveTenantAdmins(TENANT)).thenReturn(List.of(Recipient.external("Admin", "admin@example.com", null)));

        reminders(admins).remindAll(today);
        List<NotificationRequest> overdue = sent().stream().filter(n -> n.type() == NotificationType.PERF_REVIEWS_OVERDUE).toList();
        assertThat(overdue).hasSize(2);
        NotificationRequest toBoss = overdue.stream().filter(n -> emails(n).contains("boss@example.com")).findFirst().orElseThrow();
        assertThat(toBoss.title()).startsWith("2 reviews");
        assertThat(overdue.stream().anyMatch(n -> emails(n).contains("admin@example.com"))).isTrue();
    }

    @Test
    void reviewsAlreadyRatedAreNotChased() {
        email(boss, "boss@example.com");
        HrPerfCycle cycle = HrPerfCycle.of(TENANT.getValue(), "Mid-year 2026", today.minusDays(120), today.minusDays(10), today.minusDays(5), today.minusDays(1), false, hrUser);
        cycle.launch(hrUser);
        HrPerfReview done = HrPerfReview.of(TENANT.getValue(), cycle.getId(), ann, boss, "Clerk");
        done.submitSelf(4, "Fine");
        done.submitManager(4, "Delivered the project on time and mentored two new starters.", boss);
        when(h.cycleRepo.allOpen()).thenReturn(List.of(cycle));
        when(h.reviewRepo.forCycles(any())).thenReturn(List.of(done));
        assertThat(reminders(mock(TenantAdminRecipients.class)).remindAll(today)).isZero();
    }
}
