package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.EmployeeChangeRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeChange;
import za.co.handyflow.platform.hr.domain.model.HrPayrollSettings;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeChangeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayrollSettingsRepository;
import za.co.handyflow.platform.hr.dto.BankChangeRequest;
import za.co.handyflow.platform.hr.dto.CompensationChangeRequest;
import za.co.handyflow.platform.hr.dto.EmployeeChangeResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmployeeChangeServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID asker = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();

    HrEmployeeChangeRepository changeRepo;
    HrEmployeeRepository employeeRepo;
    HrPayrollSettingsRepository settingsRepo;
    EmployeeAuditService audit;
    EmployeeChangeService service;
    za.co.handyflow.platform.shared.SeparationOfDutiesChecker duties;
    HrEmployee emp;

    @BeforeEach
    void setUp() {
        changeRepo = mock(HrEmployeeChangeRepository.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        settingsRepo = mock(HrPayrollSettingsRepository.class);
        audit = mock(EmployeeAuditService.class);
        duties = mock(za.co.handyflow.platform.shared.SeparationOfDutiesChecker.class);
        service = new EmployeeChangeService(changeRepo, employeeRepo, settingsRepo, audit, mock(org.springframework.context.ApplicationEventPublisher.class), duties);

        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        emp.setBankName("FNB");
        emp.setBankAccountNumber("62000011112222");
        emp.setBankBranchCode("250655");
        when(employeeRepo.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(changeRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(changeRepo.countPending(any(), any(), any())).thenReturn(0L);
        when(settingsRepo.findById(any())).thenReturn(Optional.empty());
    }

    private CompensationChangeRequest raise(String gross, LocalDate effective) {
        return new CompensationChangeRequest(new BigDecimal(gross), null, null, null, null, effective, "Annual increase");
    }

    private HrEmployeeChange pending(LocalDate effective) {
        return HrEmployeeChange.request(tenant.getValue(), emp.getId(), EmployeeChangeRules.COMPENSATION,
                Map.of("grossSalary", "30000", "travelAllowance", "0", "medicalAidContribution", "0", "pensionContribution", "0", "payFrequency", "MONTHLY"),
                Map.of("grossSalary", "35000", "travelAllowance", "0", "medicalAidContribution", "0", "pensionContribution", "0", "payFrequency", "MONTHLY"),
                effective, "Annual increase", asker);
    }

    @Test
    void requestingDoesNotTouchTheEmployee() {
        EmployeeChangeResponse r = service.requestCompensation(tenant, emp.getId(), raise("35000", LocalDate.now().plusDays(10)), asker);

        assertThat(r.status()).isEqualTo("PENDING");
        assertThat(emp.getGrossSalary()).isEqualByComparingTo("30000");
        assertThat(r.fields()).hasSize(1);
        assertThat(r.fields().get(0).label()).isEqualTo("Gross salary");
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("CHANGE_REQUESTED"), any(), eq(asker));
    }

    @Test
    void auditDetailNamesFieldsButNotValues() {
        service.requestCompensation(tenant, emp.getId(), raise("35000", LocalDate.now().plusDays(10)), asker);

        verify(audit).record(any(), any(), any(), argThat(detail -> detail.contains("Gross salary") && !detail.contains("35000")), any());
    }

    @Test
    void requestWithNothingDifferentIsRefused() {
        assertThatThrownBy(() -> service.requestCompensation(tenant, emp.getId(), raise("30000", LocalDate.now()), asker))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Nothing is different");
    }

    @Test
    void secondOpenRequestOfTheSameKindIsRefused() {
        when(changeRepo.countPending(any(), eq(emp.getId()), eq("COMPENSATION"))).thenReturn(1L);

        assertThatThrownBy(() -> service.requestCompensation(tenant, emp.getId(), raise("35000", LocalDate.now()), asker))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already a change");
    }

    @Test
    void requestWithoutReasonIsRefused() {
        var req = new CompensationChangeRequest(new BigDecimal("35000"), null, null, null, null, LocalDate.now(), " ");
        assertThatThrownBy(() -> service.requestCompensation(tenant, emp.getId(), req, asker)).isInstanceOf(IllegalArgumentException.class);
        verify(changeRepo, never()).save(any());
    }

    @Test
    void terminatedEmployeeCannotHaveAChangeRequested() {
        emp.terminate(LocalDate.now().minusDays(1), "Resigned", asker);
        assertThatThrownBy(() -> service.requestCompensation(tenant, emp.getId(), raise("35000", LocalDate.now()), asker))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bankRequestKeepsFullNumbersButMasksThemInTheResponse() {
        var req = new BankChangeRequest("Standard Bank", "012345678", "051001", LocalDate.now().plusDays(3), "Changed bank");

        EmployeeChangeResponse r = service.requestBank(tenant, emp.getId(), req, asker);

        assertThat(r.fields().stream().anyMatch(f -> f.key().equals("bankAccountNumber") && f.newValue().equals("••••5678") && f.oldValue().equals("••••2222"))).isTrue();
        assertThat(emp.getBankAccountNumber()).isEqualTo("62000011112222");
    }

    @Test
    void bankRequestWithBadAccountIsRefused() {
        var req = new BankChangeRequest("Standard Bank", "12", "051001", LocalDate.now(), "Changed bank");
        assertThatThrownBy(() -> service.requestBank(tenant, emp.getId(), req, asker)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void askerCannotApproveTheirOwnRequest() {
        HrEmployeeChange c = pending(LocalDate.now().plusDays(10));
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.approve(tenant, c.getId(), null, asker)).isInstanceOf(AccessDeniedException.class);
        assertThat(c.getStatus()).isEqualTo("PENDING");
    }

    private void businessAllowsSelfApproval() {
        HrPayrollSettings s = HrPayrollSettings.create(tenant.getValue());
        s.setAllowSelfApproval(true, approver);
        when(settingsRepo.findById(any())).thenReturn(Optional.of(s));
    }

    private HrEmployeeChange pendingBank(java.time.Instant requestedAt) {
        HrEmployeeChange c = HrEmployeeChange.request(tenant.getValue(), emp.getId(), EmployeeChangeRules.BANK_DETAILS,
                Map.of("bankName", "FNB", "bankAccountNumber", "62000011112222", "bankBranchCode", "250655", "bankAccountType", "CHEQUE"),
                Map.of("bankName", "Capitec", "bankAccountNumber", "1234567890", "bankBranchCode", "470010", "bankAccountType", "SAVINGS"),
                LocalDate.now().plusDays(3), "Changed bank", asker);
        org.springframework.test.util.ReflectionTestUtils.setField(c, "requestedAt", requestedAt);
        return c;
    }

    @Test
    void soleApproverMayApproveOwnPayChangeWithAReasonAndItIsAuditedInFull() {
        businessAllowsSelfApproval();
        HrEmployeeChange c = pending(LocalDate.now().plusDays(10));
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        EmployeeChangeResponse r = service.approve(tenant, c.getId(), "Sole director, annual increase agreed in writing", asker);

        assertThat(r.selfApproved()).isTrue();
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("CHANGE_APPROVED_OWN"), any(), eq(asker));
        @SuppressWarnings("unchecked") org.mockito.ArgumentCaptor<Map<String, ?>> details = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(duties).recordSoleException(eq(tenant), eq(asker), eq("HR_COMPENSATION_APPROVE"), eq("EMPLOYEE_CHANGE"), eq(c.getId()), any(), details.capture());
        assertThat(details.getValue()).containsEntry("reason", "Sole director, annual increase agreed in writing")
                .containsEntry("initiator", asker).containsEntry("approver", asker).containsKeys("requestedAt", "approvedAt", "before", "after");
    }

    @Test
    void soleApproverCannotApproveOwnChangeWithoutAReason() {
        businessAllowsSelfApproval();
        HrEmployeeChange c = pending(LocalDate.now().plusDays(10));
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.approve(tenant, c.getId(), "  ", asker)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> service.approve(tenant, c.getId(), "short", asker)).isInstanceOf(AccessDeniedException.class);
        assertThat(c.getStatus()).isEqualTo("PENDING");
        verify(duties, never()).recordSoleException(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void settingDoesNotLetAskerApproveWhileAnotherApproverExists() {
        businessAllowsSelfApproval();
        when(duties.otherHolders(any(), eq("HR_COMPENSATION_APPROVE"), eq(asker))).thenReturn(1L);
        HrEmployeeChange c = pending(LocalDate.now().plusDays(10));
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.approve(tenant, c.getId(), "Sole director, annual increase agreed", asker)).isInstanceOf(AccessDeniedException.class);
        assertThat(c.getStatus()).isEqualTo("PENDING");
        verify(duties, never()).recordSoleException(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ownBankChangeCannotBeApprovedUntil24HoursAfterTheRequest() {
        businessAllowsSelfApproval();
        HrEmployeeChange c = pendingBank(java.time.Instant.now().minus(java.time.Duration.ofHours(2)));
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.approve(tenant, c.getId(), "Sole director, new business account", asker))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("24 hours");
        assertThat(c.getStatus()).isEqualTo("PENDING");
        verify(duties, never()).recordSoleException(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ownBankChangeIsApprovedAfter24HoursWithAReasonAndTheAccountNumberIsMaskedInTheAudit() {
        businessAllowsSelfApproval();
        HrEmployeeChange c = pendingBank(java.time.Instant.now().minus(java.time.Duration.ofHours(25)));
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        service.approve(tenant, c.getId(), "Sole director, new business account", asker);

        @SuppressWarnings("unchecked") org.mockito.ArgumentCaptor<Map<String, ?>> details = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(duties).recordSoleException(any(), eq(asker), any(), eq("EMPLOYEE_CHANGE"), eq(c.getId()), any(), details.capture());
        assertThat(String.valueOf(details.getValue().get("after"))).contains("****7890").doesNotContain("1234567890");
        assertThat(String.valueOf(details.getValue().get("before"))).contains("****2222").doesNotContain("62000011112222");
    }

    @Test
    void approvalOfAFutureChangeWaitsForItsDate() {
        HrEmployeeChange c = pending(LocalDate.now().plusDays(10));
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        EmployeeChangeResponse r = service.approve(tenant, c.getId(), "ok", approver);

        assertThat(r.status()).isEqualTo("APPROVED");
        assertThat(emp.getGrossSalary()).isEqualByComparingTo("30000");
    }

    @Test
    void approvalOfAChangeDueTodayAppliesIt() {
        HrEmployeeChange c = pending(LocalDate.now());
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        EmployeeChangeResponse r = service.approve(tenant, c.getId(), null, approver);

        assertThat(r.status()).isEqualTo("APPLIED");
        assertThat(emp.getGrossSalary()).isEqualByComparingTo("35000");
        verify(employeeRepo).save(emp);
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("CHANGE_APPLIED"), any(), any());
    }

    @Test
    void rejectingNeedsAReasonAndLeavesPayAlone() {
        HrEmployeeChange c = pending(LocalDate.now());
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.reject(tenant, c.getId(), " ", approver)).isInstanceOf(IllegalArgumentException.class);
        EmployeeChangeResponse r = service.reject(tenant, c.getId(), "Not budgeted", approver);

        assertThat(r.status()).isEqualTo("REJECTED");
        assertThat(emp.getGrossSalary()).isEqualByComparingTo("30000");
    }

    @Test
    void onlyTheAskerCanCancel() {
        HrEmployeeChange c = pending(LocalDate.now().plusDays(5));
        when(changeRepo.findByTenantAndId(any(), eq(c.getId()))).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.cancel(tenant, c.getId(), approver)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.cancel(tenant, c.getId(), asker).status()).isEqualTo("CANCELLED");
    }

    @Test
    void applyDueAppliesApprovedChangesOldestFirstSoTheLaterWins() {
        HrEmployeeChange first = pending(LocalDate.now().minusDays(10));
        first.approve(approver, null, false);
        HrEmployeeChange second = HrEmployeeChange.request(tenant.getValue(), emp.getId(), EmployeeChangeRules.COMPENSATION,
                Map.of("grossSalary", "35000"), Map.of("grossSalary", "40000", "travelAllowance", "0", "medicalAidContribution", "0", "pensionContribution", "0", "payFrequency", "MONTHLY"),
                LocalDate.now().minusDays(1), "Promotion", asker);
        second.approve(approver, null, false);
        when(changeRepo.due(any(), any())).thenReturn(List.of(first, second));

        int n = service.applyDue(tenant, LocalDate.now());

        assertThat(n).isEqualTo(2);
        assertThat(emp.getGrossSalary()).isEqualByComparingTo("40000");
        assertThat(first.getStatus()).isEqualTo("APPLIED");
        assertThat(second.getStatus()).isEqualTo("APPLIED");
    }
}
