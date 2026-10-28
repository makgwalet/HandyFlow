package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.hr.domain.model.ErPolicy;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrErCaseRepository;
import za.co.handyflow.platform.hr.dto.ErDtos.Row;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Who is reminded about a case step, and that the words never name the person or the matter. */
class ErReminderServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final TenantId tenant = TenantId.of(tenantId);
    private final UUID owner = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 9);
    private final HrErCaseRepository caseRepo = mock(HrErCaseRepository.class);
    private final ErCaseViews views = mock(ErCaseViews.class);
    private final ErPolicyService policyService = mock(ErPolicyService.class);
    private final HrEmployeeRepository employeeRepo = mock(HrEmployeeRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final TenantAdminRecipients admins = mock(TenantAdminRecipients.class);
    private final ErReminderService service = new ErReminderService(caseRepo, views, policyService, employeeRepo,
            new PerfNotifier(notificationService, employeeRepo), admins);

    private HrErCase aCase(UUID ownerUser) {
        return HrErCase.open(tenantId, "ER-2026-007", "DISCIPLINARY", "MISCONDUCT", UUID.randomUUID(), "Theft of stock", "Secret details",
                today.minusDays(10), today.minusDays(9), true, UUID.randomUUID(), ownerUser);
    }

    private Row rowDue(LocalDate due, String label) {
        return new Row(UUID.randomUUID(), "ER-2026-007", "DISCIPLINARY", "MISCONDUCT", "Theft of stock", "NOTICE_ISSUED", UUID.randomUUID(),
                "Thandi Nkosi", true, false, today.minusDays(9), null, due, label, due != null && due.isBefore(today));
    }

    private HrEmployee ownerEmployee(String email, String status) {
        HrEmployee e = mock(HrEmployee.class);
        lenient().when(e.getEmail()).thenReturn(email);
        lenient().when(e.getFullName()).thenReturn("Olive Owner");
        lenient().when(e.getStatus()).thenReturn(status);
        return e;
    }

    private void caseWith(HrErCase c, Row row) {
        when(caseRepo.allOpen()).thenReturn(List.of(c));
        when(policyService.get(any())).thenReturn(ErPolicy.defaults());
        when(views.row(any(), any(), any(), any())).thenReturn(row);
    }

    private List<NotificationRequest> sent() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void aMeetingTomorrowIsRemindedToTheOwnerWithoutNamingAnyone() {
        caseWith(aCase(owner), rowDue(today.plusDays(1), "Meeting"));
        HrEmployee mocked1 = ownerEmployee("olive@example.com", "ACTIVE");
        when(employeeRepo.findByUserId(any(), eq(owner))).thenReturn(Optional.of(mocked1));

        assertThat(service.remindAll(today)).isEqualTo(1);

        NotificationRequest n = sent().get(0);
        assertThat(n.type()).isEqualTo(NotificationType.ER_CASE_DUE);
        assertThat(n.recipients().get(0).email()).isEqualTo("olive@example.com");
        assertThat(n.title()).isEqualTo("ER-2026-007: meeting is due tomorrow");
        assertThat(n.message()).doesNotContain("Thandi").doesNotContain("Theft").doesNotContain("Secret").doesNotContain("DISCIPLINARY");
    }

    @Test
    void aStepThatIsNotDueTomorrowOrLateIsLeftAlone() {
        caseWith(aCase(owner), rowDue(today.plusDays(5), "Meeting"));
        assertThat(service.remindAll(today)).isZero();
        assertThat(sent()).isEmpty();
    }

    @Test
    void aDecisionNobodyToldTheEmployeeAboutIsLateFromTheDayAfter() {
        HrErCase c = aCase(owner);
        c.decide("WRITTEN_WARNING", today.minusDays(1), "note", UUID.randomUUID(), 6, null);
        caseWith(c, rowDue(null, "Tell the employee"));
        HrEmployee mocked2 = ownerEmployee("olive@example.com", "ACTIVE");
        when(employeeRepo.findByUserId(any(), eq(owner))).thenReturn(Optional.of(mocked2));

        assertThat(service.remindAll(today)).isEqualTo(1);
        assertThat(sent().get(0).title()).isEqualTo("ER-2026-007: telling the employee the decision is late");
    }

    @Test
    void aCaseWithoutAnOwnerGoesToTheAdministrators() {
        caseWith(aCase(null), rowDue(today.minusDays(1), "Response due"));
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of(Recipient.external("HR", "hr@example.com", null)));

        assertThat(service.remindAll(today)).isEqualTo(1);
        assertThat(sent().get(0).recipients().get(0).email()).isEqualTo("hr@example.com");
    }

    @Test
    void anOwnerWhoHasLeftIsReplacedByTheAdministrators() {
        caseWith(aCase(owner), rowDue(today.minusDays(1), "Response due"));
        HrEmployee mocked3 = ownerEmployee("olive@example.com", "TERMINATED");
        when(employeeRepo.findByUserId(any(), eq(owner))).thenReturn(Optional.of(mocked3));
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of(Recipient.external("HR", "hr@example.com", null)));

        assertThat(service.remindAll(today)).isEqualTo(1);
        assertThat(sent().get(0).recipients().get(0).email()).isEqualTo("hr@example.com");
    }

    @Test
    void casesRecordedFromBeforeTheSystemAreSkipped() {
        HrErCase legacy = mock(HrErCase.class);
        when(legacy.isLegacy()).thenReturn(true);
        when(caseRepo.allOpen()).thenReturn(List.of(legacy));
        assertThat(service.remindAll(today)).isZero();
        verifyNoInteractions(views);
    }

    @Test
    void oneCaseFailingDoesNotStopTheNext() {
        HrErCase broken = aCase(owner), fine = aCase(owner);
        when(caseRepo.allOpen()).thenReturn(List.of(broken, fine));
        when(policyService.get(any())).thenReturn(ErPolicy.defaults());
        when(views.row(eq(broken), any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        when(views.row(eq(fine), any(), any(), any())).thenReturn(rowDue(today.plusDays(1), "Meeting"));
        HrEmployee mocked4 = ownerEmployee("olive@example.com", "ACTIVE");
        when(employeeRepo.findByUserId(any(), eq(owner))).thenReturn(Optional.of(mocked4));

        assertThat(service.remindAll(today)).isEqualTo(1);
    }

    // ── Hearing, suspension and review ───────────────────────────────────────────

    private HrEmployee person(String name, String email) {
        HrEmployee e = mock(HrEmployee.class);
        lenient().when(e.getEmail()).thenReturn(email);
        lenient().when(e.getFullName()).thenReturn(name);
        lenient().when(e.getStatus()).thenReturn("ACTIVE");
        return e;
    }

    private void idle(HrErCase c) {
        // No step is due, so only the new reminders can send.
        caseWith(c, rowDue(null, "Case step"));
    }

    @Test
    void theDayBeforeAHearingTheChairTheInitiatorAndTheEmployeeEachAreTold() {
        HrErCase c = aCase(owner);
        UUID chair = UUID.randomUUID(), initiator = UUID.randomUUID();
        c.issueNotice(java.time.Instant.now(), today.plusDays(1), java.time.LocalTime.of(9, 30), "Boardroom", chair, initiator);
        idle(c);
        HrEmployee chairP = person("Charles Chair", "chair@example.com");
        HrEmployee initP = person("Ina Initiator", "init@example.com");
        HrEmployee empP = person("Thandi Nkosi", "thandi@example.com");
        when(employeeRepo.findActiveById(any(), eq(chair))).thenReturn(Optional.of(chairP));
        when(employeeRepo.findActiveById(any(), eq(initiator))).thenReturn(Optional.of(initP));
        when(employeeRepo.findActiveById(any(), eq(c.getEmployeeId()))).thenReturn(Optional.of(empP));

        assertThat(service.remindAll(today)).isEqualTo(3);

        List<NotificationRequest> out = sent();
        assertThat(out).extracting(n -> n.recipients().get(0).email()).containsExactly("chair@example.com", "init@example.com", "thandi@example.com");
        assertThat(out.get(0).message()).contains("You chair");
        assertThat(out.get(2).message()).contains("Your hearing").contains("09:30");
        for (NotificationRequest n : out) assertThat(n.message()).doesNotContain("Theft").doesNotContain("Secret").doesNotContain("Boardroom");
    }

    @Test
    void aPartyWhoHasLeftOrHasNoEmailIsSkippedAndTheOthersStillGetTheirs() {
        HrErCase c = aCase(owner);
        UUID chair = UUID.randomUUID();
        c.issueNotice(java.time.Instant.now(), today.plusDays(1), null, null, chair, null);
        idle(c);
        HrEmployee gone = person("Charles Chair", "chair@example.com");
        when(gone.getStatus()).thenReturn("TERMINATED");
        HrEmployee empP = person("Thandi Nkosi", "thandi@example.com");
        when(employeeRepo.findActiveById(any(), eq(chair))).thenReturn(Optional.of(gone));
        when(employeeRepo.findActiveById(any(), eq(c.getEmployeeId()))).thenReturn(Optional.of(empP));

        assertThat(service.remindAll(today)).isEqualTo(1);
    }

    @Test
    void aHearingNotTomorrowSendsNothing() {
        HrErCase c = aCase(owner);
        c.issueNotice(java.time.Instant.now(), today.plusDays(4), null, null, UUID.randomUUID(), UUID.randomUUID());
        idle(c);

        assertThat(service.remindAll(today)).isZero();
    }

    @Test
    void theOwnerIsToldTheDayBeforeASuspensionEnds() {
        HrErCase c = aCase(owner);
        c.suspend(today.minusDays(5), today.plusDays(1), "Investigation");
        idle(c);
        HrEmployee mocked = ownerEmployee("olive@example.com", "ACTIVE");
        when(employeeRepo.findByUserId(any(), eq(owner))).thenReturn(Optional.of(mocked));

        assertThat(service.remindAll(today)).isEqualTo(1);
        NotificationRequest n = sent().get(0);
        assertThat(n.title()).isEqualTo("ER-2026-007: suspension ends tomorrow");
        assertThat(n.message()).doesNotContain("Investigation").doesNotContain("Thandi");
    }

    @Test
    void anOpenEndedSuspensionGoesToTheAdministratorsWhenThereIsNoOwner() {
        HrErCase c = aCase(null);
        c.suspend(today.minusDays(30), null, "Investigation");
        idle(c);
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of(Recipient.external("HR", "hr@example.com", null)));

        assertThat(service.remindAll(today)).isEqualTo(1);
        assertThat(sent().get(0).title()).contains("no end date");
    }

    @Test
    void theOwnerIsToldWhenAReviewPeriodEndsWithNoOutcome() {
        HrErCase c = aCase(owner);
        c.setReview(today.minusDays(60), today.minusDays(1));
        idle(c);
        HrEmployee mocked = ownerEmployee("olive@example.com", "ACTIVE");
        when(employeeRepo.findByUserId(any(), eq(owner))).thenReturn(Optional.of(mocked));

        assertThat(service.remindAll(today)).isEqualTo(1);
        assertThat(sent().get(0).title()).isEqualTo("ER-2026-007: review period has ended");
    }
}
