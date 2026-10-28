package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrOnboardingRepository;
import za.co.handyflow.platform.hr.domain.repository.HrOnboardingTaskRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Who hears about which overdue onboarding task, and that nothing is sent when nothing is late. */
class OnboardingReminderServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    private HrOnboardingRepository onboardingRepo;
    private HrOnboardingTaskRepository taskRepo;
    private HrEmployeeRepository employeeRepo;
    private NotificationService notifications;
    private TenantAdminRecipients admins;
    private UserRecipientResolver users;
    private ReminderLedger ledger;
    private OnboardingReminderService service;

    private HrEmployee employee;
    private HrOnboarding onboarding;

    @BeforeEach
    void setUp() {
        onboardingRepo = mock(HrOnboardingRepository.class);
        taskRepo = mock(HrOnboardingTaskRepository.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        notifications = mock(NotificationService.class);
        admins = mock(TenantAdminRecipients.class);
        users = mock(UserRecipientResolver.class);
        ledger = mock(ReminderLedger.class);
        // By default nobody has had the message yet, so everyone is sent it.
        when(ledger.unclaimed(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(6));
        service = new OnboardingReminderService(onboardingRepo, taskRepo, employeeRepo, notifications, admins, users, ledger);

        employee = HrEmployee.create(tenant, "E-1", "Thandi", "Mokoena", TODAY.minusDays(20), "PERMANENT", new BigDecimal("20000"), "MONTHLY");
        onboarding = HrOnboarding.start(tenant.getValue(), employee.getId(), TODAY.minusDays(20), TODAY.plusMonths(2), UUID.randomUUID());
        when(onboardingRepo.allInProgress()).thenReturn(List.of(onboarding));
        when(employeeRepo.findAllById(any())).thenReturn(List.of(employee));
        when(admins.resolveTenantAdmins(tenant)).thenReturn(List.of(Recipient.external("HR Admin", "hr@example.com", null)));
        when(users.resolveUser(any(), any())).thenReturn(Optional.empty());
    }

    private HrOnboardingTask task(String title, boolean required, int daysAgoDue, UUID assignee) {
        HrOnboardingTask t = HrOnboardingTask.of(tenant.getValue(), onboarding.getId(),
                new OnboardingRules.Spec(title, "", "DOCUMENTS", required, 0, OnboardingRules.START, null), TODAY.minusDays(daysAgoDue), 0);
        if (assignee != null) t.assignTo(assignee);
        return t;
    }

    private void tasks(HrOnboardingTask... t) { when(taskRepo.forOnboardings(any())).thenReturn(List.of(t)); }

    private NotificationRequest onlySent() {
        var captor = org.mockito.ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void anUnassignedLateTaskGoesToTheAdministratorsWhenThereIsNoManager() {
        tasks(task("Bank details recorded", true, 1, null));
        assertThat(service.remindAll(TODAY)).isEqualTo(1);
        NotificationRequest sent = onlySent();
        assertThat(sent.type()).isEqualTo(NotificationType.ONBOARDING_TASKS_OVERDUE);
        assertThat(sent.recipients()).extracting(Recipient::email).containsExactly("hr@example.com");
        assertThat(sent.message()).contains("Bank details recorded (1 day late)");
    }

    @Test
    void theManagerHearsInsteadOfTheAdministratorsWhenThereIsOne() {
        HrEmployee boss = HrEmployee.create(tenant, "E-0", "Pat", "Boss", TODAY.minusYears(3), "PERMANENT", new BigDecimal("50000"), "MONTHLY");
        boss.setEmail("pat@example.com");
        employee.setManagerId(boss.getId());
        when(employeeRepo.findActiveById(tenant, boss.getId())).thenReturn(Optional.of(boss));
        tasks(task("Induction held", true, 1, null));
        service.remindAll(TODAY);
        assertThat(onlySent().recipients()).extracting(Recipient::email).containsExactly("pat@example.com");
    }

    @Test
    void anAssignedTaskGoesToTheAssignee() {
        UUID assignee = UUID.randomUUID();
        when(users.resolveUser(tenant, assignee)).thenReturn(Optional.of(Recipient.user(assignee, "Sipho", "sipho@example.com", null)));
        tasks(task("Equipment issued", false, 1, assignee));
        service.remindAll(TODAY);
        assertThat(onlySent().recipients()).extracting(Recipient::email).containsExactly("sipho@example.com");
    }

    @Test
    void anAssigneeWhoCannotBeReachedFallsBackToTheAdministrators() {
        tasks(task("Equipment issued", false, 1, UUID.randomUUID()));
        service.remindAll(TODAY);
        assertThat(onlySent().recipients()).extracting(Recipient::email).containsExactly("hr@example.com");
    }

    @Test
    void nothingIsSentForATaskThatIsNotLateOrWasMentionedRecently() {
        tasks(task("Not yet due", true, -3, null), task("Late but not today", true, 3, null));
        assertThat(service.remindAll(TODAY)).isZero();
        verify(notifications, never()).send(any());
    }

    @Test
    void aTaskThatTheEmployeeRecordNowSatisfiesIsNotMentioned() {
        // a checked task (salary) is done as soon as the record has a salary, whatever its stored status says
        HrOnboardingTask checked = HrOnboardingTask.of(tenant.getValue(), onboarding.getId(),
                new OnboardingRules.Spec("Salary and pay frequency set", "", "PAYROLL", true, 0, OnboardingRules.START, "SALARY"), TODAY.minusDays(1), 0);
        tasks(checked);
        assertThat(service.remindAll(TODAY)).isZero();
    }

    @Test
    void aLeaverIsNotChased() {
        employee.terminate(TODAY.minusDays(1));
        tasks(task("Induction held", true, 1, null));
        assertThat(service.remindAll(TODAY)).isZero();
    }

    @Test
    void aFailureWhileSendingIsLoggedAndDoesNotStopTheRun() {
        tasks(task("Induction held", true, 1, null));
        doThrow(new IllegalStateException("mail down")).when(notifications).send(any());
        assertThat(service.remindAll(TODAY)).isZero();
    }

    @Test
    void aMessageThePeopleAlreadyHadTodayIsNotSentAgain() {
        when(ledger.unclaimed(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        tasks(task("Bank details recorded", true, 1, null));
        service.remindAll(TODAY);
        verify(notifications, never()).send(any());
    }

    @Test
    void theClaimsAreGivenBackWhenSendingFails() {
        when(ledger.unclaimed(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            List<String> claimed = inv.getArgument(7);
            claimed.add("digest-1");
            return inv.getArgument(6);
        });
        doThrow(new IllegalStateException("mail down")).when(notifications).send(any());
        tasks(task("Bank details recorded", true, 1, null));
        service.remindAll(TODAY);
        verify(ledger).release(eq(tenant), eq(TODAY), eq("digest-1"));
    }

    @Test
    void aLedgerThatCannotBeReachedNeverStopsTheReminder() {
        when(ledger.unclaimed(any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("db"));
        tasks(task("Bank details recorded", true, 1, null));
        assertThat(service.remindAll(TODAY)).isEqualTo(1);
        verify(notifications).send(any());
    }
}
