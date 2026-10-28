package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrLetter;
import za.co.handyflow.platform.hr.domain.model.HrPerfGoal;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrLetterRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPerfGoalRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Who is reminded about a letter or a goal, and that a letter's matter is never in the words. */
class WorkReminderServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 10, 28);
    private final HrLetterRepository letterRepo = mock(HrLetterRepository.class);
    private final HrPerfGoalRepository goalRepo = mock(HrPerfGoalRepository.class);
    private final HrEmployeeRepository employeeRepo = mock(HrEmployeeRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final TenantAdminRecipients admins = mock(TenantAdminRecipients.class);
    private final WorkReminderService service = new WorkReminderService(letterRepo, goalRepo, employeeRepo,
            new PerfNotifier(notificationService, employeeRepo), admins);

    private HrEmployee person(String email, String status, UUID manager) {
        HrEmployee e = mock(HrEmployee.class);
        lenient().when(e.getEmail()).thenReturn(email);
        lenient().when(e.getFullName()).thenReturn("Thandi Nkosi");
        lenient().when(e.getStatus()).thenReturn(status);
        lenient().when(e.getManagerId()).thenReturn(manager);
        return e;
    }

    private HrLetter letter(LocalDate issuedOn) {
        HrLetter l = mock(HrLetter.class);
        lenient().when(l.getTenantId()).thenReturn(tenantId);
        lenient().when(l.getId()).thenReturn(UUID.randomUUID());
        lenient().when(l.getEmployeeId()).thenReturn(employeeId);
        lenient().when(l.getNumber()).thenReturn("LTR-2026-0004");
        lenient().when(l.getStatus()).thenReturn("ISSUED");
        lenient().when(l.isRequiresAck()).thenReturn(true);
        lenient().when(l.getAcknowledgedAt()).thenReturn(null);
        lenient().when(l.getIssuedOn()).thenReturn(issuedOn);
        lenient().when(l.getSubject()).thenReturn("Final written warning");
        return l;
    }

    private HrPerfGoal goal(String status, LocalDate due, Instant createdAt) {
        HrPerfGoal g = mock(HrPerfGoal.class);
        lenient().when(g.getTenantId()).thenReturn(tenantId);
        lenient().when(g.getId()).thenReturn(UUID.randomUUID());
        lenient().when(g.getEmployeeId()).thenReturn(employeeId);
        lenient().when(g.getTitle()).thenReturn("Cut onboarding time");
        lenient().when(g.getStatus()).thenReturn(status);
        lenient().when(g.getDueDate()).thenReturn(due);
        lenient().when(g.getCreatedAt()).thenReturn(createdAt);
        return g;
    }

    private List<NotificationRequest> sent() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void anUnacknowledgedLetterIsChasedWithoutSayingWhatItIsAbout() {
        HrLetter l = letter(today.minusDays(8));
        when(letterRepo.awaitingAcknowledgement()).thenReturn(List.of(l));
        HrEmployee thandi = person("thandi@example.com", "ACTIVE", null);
        when(employeeRepo.findActiveById(any(), eq(employeeId))).thenReturn(Optional.of(thandi));

        assertThat(service.remindAll(today)).isEqualTo(1);

        NotificationRequest n = sent().get(0);
        assertThat(n.type()).isEqualTo(NotificationType.LETTER_UNACKNOWLEDGED);
        assertThat(n.message()).contains("LTR-2026-0004").doesNotContain("warning");
        assertThat(n.title()).doesNotContain("warning");
    }

    @Test
    void aLetterWhoseEmployeeHasLeftOrHasNoEmailGetsNothing() {
        HrLetter l = letter(today.minusDays(8));
        when(letterRepo.awaitingAcknowledgement()).thenReturn(List.of(l));
        HrEmployee gone = person("thandi@example.com", "TERMINATED", null);
        when(employeeRepo.findActiveById(any(), eq(employeeId))).thenReturn(Optional.of(gone));
        assertThat(service.remindAll(today)).isZero();

        HrEmployee noEmail = person(null, "ACTIVE", null);
        when(employeeRepo.findActiveById(any(), eq(employeeId))).thenReturn(Optional.of(noEmail));
        assertThat(service.remindAll(today)).isZero();
    }

    @Test
    void aGoalPastItsDateIsRemindedToTheEmployee() {
        HrPerfGoal g = goal("ACTIVE", today.minusDays(1), Instant.parse("2026-01-05T08:00:00Z"));
        when(goalRepo.allOpen()).thenReturn(List.of(g));
        HrEmployee thandi = person("thandi@example.com", "ACTIVE", managerId);
        when(employeeRepo.findActiveById(any(), eq(employeeId))).thenReturn(Optional.of(thandi));

        assertThat(service.remindAll(today)).isEqualTo(1);
        assertThat(sent().get(0).type()).isEqualTo(NotificationType.PERF_GOAL_OVERDUE);
    }

    @Test
    void aProposalNobodyApprovedGoesToTheManager() {
        HrPerfGoal g = goal("PROPOSED", today.plusDays(60), today.minusDays(6).atStartOfDay(java.time.ZoneId.of("Africa/Johannesburg")).plusHours(9).toInstant());
        when(goalRepo.allOpen()).thenReturn(List.of(g));
        HrEmployee thandi = person("thandi@example.com", "ACTIVE", managerId);
        HrEmployee boss = person("boss@example.com", "ACTIVE", null);
        when(employeeRepo.findActiveById(any(), eq(employeeId))).thenReturn(Optional.of(thandi));
        when(employeeRepo.findActiveById(any(), eq(managerId))).thenReturn(Optional.of(boss));

        assertThat(service.remindAll(today)).isEqualTo(1);

        NotificationRequest n = sent().get(0);
        assertThat(n.type()).isEqualTo(NotificationType.PERF_GOAL_WAITING);
        assertThat(n.recipients().get(0).email()).isEqualTo("boss@example.com");
    }

    @Test
    void aProposalWithNoManagerGoesToTheAdministrators() {
        HrPerfGoal g = goal("PROPOSED", today.plusDays(60), today.minusDays(6).atStartOfDay(java.time.ZoneId.of("Africa/Johannesburg")).plusHours(9).toInstant());
        when(goalRepo.allOpen()).thenReturn(List.of(g));
        HrEmployee thandi = person("thandi@example.com", "ACTIVE", null);
        when(employeeRepo.findActiveById(any(), eq(employeeId))).thenReturn(Optional.of(thandi));
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of(Recipient.external("HR", "hr@example.com", null)));

        assertThat(service.remindAll(today)).isEqualTo(1);
        assertThat(sent().get(0).recipients().get(0).email()).isEqualTo("hr@example.com");
    }

    @Test
    void oneItemFailingDoesNotStopTheOthers() {
        HrLetter bad = letter(today.minusDays(8));
        when(bad.getEmployeeId()).thenThrow(new IllegalStateException("boom"));
        HrLetter good = letter(today.minusDays(8));
        when(letterRepo.awaitingAcknowledgement()).thenReturn(List.of(bad, good));
        HrEmployee thandi = person("thandi@example.com", "ACTIVE", null);
        when(employeeRepo.findActiveById(any(), eq(employeeId))).thenReturn(Optional.of(thandi));

        assertThat(service.remindAll(today)).isEqualTo(1);
    }
}
