package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrLeavePolicy;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrLeavePolicyRepository;
import za.co.handyflow.platform.hr.dto.LeavePolicyDtos.AtRisk;
import za.co.handyflow.platform.hr.dto.LeavePolicyDtos.AtRiskReport;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
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

/** Who is told that annual leave will be lost at the rollover, and on which days. */
class LeaveReminderServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final HrLeavePolicyRepository policyRepo = mock(HrLeavePolicyRepository.class);
    private final LeaveService leaveService = mock(LeaveService.class);
    private final HrEmployeeRepository employeeRepo = mock(HrEmployeeRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final TenantAdminRecipients admins = mock(TenantAdminRecipients.class);
    private final LeaveReminderService service = new LeaveReminderService(policyRepo, leaveService, employeeRepo,
            new PerfNotifier(notificationService, employeeRepo), admins);

    private final LocalDate firstOfNovember = LocalDate.of(2026, 11, 1);

    private HrLeavePolicy policy(int effectiveYear) {
        HrLeavePolicy p = HrLeavePolicy.none(tenant.getValue());
        p.set(new BigDecimal("10"), effectiveYear, "Leave policy v2", UUID.randomUUID());
        return p;
    }

    private HrEmployee person(String email, String status) {
        HrEmployee e = mock(HrEmployee.class);
        lenient().when(e.getEmail()).thenReturn(email);
        lenient().when(e.getFullName()).thenReturn("Thandi Nkosi");
        lenient().when(e.getStatus()).thenReturn(status);
        return e;
    }

    private List<NotificationRequest> sent() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void nothingGoesOutOnADayThatIsNotAReminderDay() {
        when(policyRepo.findAll()).thenReturn(List.of(policy(2026)));
        assertThat(service.remindAll(LocalDate.of(2026, 11, 2))).isZero();
        verifyNoInteractions(leaveService);
    }

    @Test
    void aCompanyWithoutALimitThatAppliesIsNotLookedAt() {
        when(policyRepo.findAll()).thenReturn(List.of(policy(2028)));
        assertThat(service.remindAll(firstOfNovember)).isZero();
        verifyNoInteractions(leaveService);
    }

    @Test
    void anEmployeeAtRiskIsToldAndTheAdministratorsGetACount() {
        UUID emp = UUID.randomUUID();
        when(policyRepo.findAll()).thenReturn(List.of(policy(2027)));
        when(leaveService.atRisk(any())).thenReturn(new AtRiskReport(new BigDecimal("10"), 2027,
                List.of(new AtRisk(emp, "Thandi Nkosi", new BigDecimal("14"), new BigDecimal("16"), new BigDecimal("6")))));
        when(employeeRepo.findActiveById(any(), eq(emp))).thenReturn(Optional.of(person("thandi@example.com", "ACTIVE")));
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of(Recipient.external("HR", "hr@example.com", null)));

        assertThat(service.remindAll(firstOfNovember)).isEqualTo(2);

        List<NotificationRequest> out = sent();
        assertThat(out).allMatch(n -> n.type() == NotificationType.LEAVE_AT_RISK);
        NotificationRequest toEmployee = out.stream().filter(n -> n.recipients().get(0).email().equals("thandi@example.com")).findFirst().orElseThrow();
        assertThat(toEmployee.message()).contains("lose 6 days").contains("at most 10 days");
        NotificationRequest toAdmins = out.stream().filter(n -> n.recipients().get(0).email().equals("hr@example.com")).findFirst().orElseThrow();
        assertThat(toAdmins.title()).startsWith("1 employee");
        assertThat(toAdmins.message()).doesNotContain("Thandi");
    }

    @Test
    void someoneWithoutAnEmailOrWhoHasLeftIsSkipped() {
        UUID noEmail = UUID.randomUUID(), gone = UUID.randomUUID();
        when(policyRepo.findAll()).thenReturn(List.of(policy(2027)));
        when(leaveService.atRisk(any())).thenReturn(new AtRiskReport(new BigDecimal("10"), 2027, List.of(
                new AtRisk(noEmail, "A", new BigDecimal("14"), new BigDecimal("16"), new BigDecimal("6")),
                new AtRisk(gone, "B", new BigDecimal("14"), new BigDecimal("16"), new BigDecimal("6")))));
        when(employeeRepo.findActiveById(any(), eq(noEmail))).thenReturn(Optional.of(person(null, "ACTIVE")));
        when(employeeRepo.findActiveById(any(), eq(gone))).thenReturn(Optional.of(person("b@example.com", "TERMINATED")));
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of());

        assertThat(service.remindAll(firstOfNovember)).isZero();
        assertThat(sent()).isEmpty();
    }

    @Test
    void oneCompanyFailingDoesNotStopTheOthers() {
        UUID emp = UUID.randomUUID();
        HrLeavePolicy broken = policy(2027), fine = HrLeavePolicy.none(UUID.randomUUID());
        fine.set(new BigDecimal("10"), 2027, "Policy", UUID.randomUUID());
        when(policyRepo.findAll()).thenReturn(List.of(broken, fine));
        when(leaveService.atRisk(TenantId.of(broken.getTenantId()))).thenThrow(new IllegalStateException("boom"));
        when(leaveService.atRisk(TenantId.of(fine.getTenantId()))).thenReturn(new AtRiskReport(new BigDecimal("10"), 2027,
                List.of(new AtRisk(emp, "C", new BigDecimal("14"), new BigDecimal("16"), new BigDecimal("6")))));
        when(employeeRepo.findActiveById(any(), eq(emp))).thenReturn(Optional.of(person("c@example.com", "ACTIVE")));
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of());

        assertThat(service.remindAll(firstOfNovember)).isEqualTo(1);
    }
}
