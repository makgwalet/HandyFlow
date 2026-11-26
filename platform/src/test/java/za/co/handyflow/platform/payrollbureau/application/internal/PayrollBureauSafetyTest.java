package za.co.handyflow.platform.payrollbureau.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Payroll Bureau safety fixes, audit 2026-11-26 (D1, D9, D11). */
@ExtendWith(MockitoExtension.class)
class PayrollBureauSafetyTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));

    @Mock private PayRunRepository payRunRepo;
    @Mock private PayslipRepository payslipRepo;
    @Mock private PayEmployeeRepository employeeRepo;
    @Mock private PayrollBureauEngine engine;
    @InjectMocks private PayrollBureauService service;

    private final UUID runId = UUID.randomUUID();

    private PayRun draftRun() {
        return PayRun.create(TENANT.getValue(), UUID.randomUUID(), "PR00001",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 3, 31), 2027);
    }

    @Test
    void payslipsOfAnotherTenantsRunAreNotFound() {
        when(payRunRepo.findByTenantAndId(TENANT.getValue(), runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayslips(TENANT, runId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(payslipRepo, never()).findByPayRun(any(), any());
    }

    @Test
    void payslipsAreReadWithTheTenantFilter() {
        when(payRunRepo.findByTenantAndId(TENANT.getValue(), runId)).thenReturn(Optional.of(draftRun()));
        when(payslipRepo.findByPayRun(TENANT.getValue(), runId)).thenReturn(List.of());

        assertThat(service.getPayslips(TENANT, runId)).isEmpty();
        verify(payslipRepo).findByPayRun(TENANT.getValue(), runId);
    }

    @Test
    void aRunIsNotProcessedWhenTheTaxYearHasNoApprovedRules() {
        PayRun run = draftRun();
        when(payRunRepo.findByTenantAndId(TENANT.getValue(), runId)).thenReturn(Optional.of(run));
        za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee emp = mock(za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee.class);
        when(emp.getGrossSalary()).thenReturn(new java.math.BigDecimal("20000"));
        when(employeeRepo.findActiveUngrouped(run.getPayClientId())).thenReturn(List.of(emp));
        when(engine.requireRules(2027)).thenThrow(new HandyFlowException(
                "There are no approved statutory rules for tax year 2026/27.", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "NO_TAX_RULES"));

        assertThatThrownBy(() -> service.processPayRun(TENANT, runId))
                .isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("2027");
        assertThat(run.getStatus()).isEqualTo("DRAFT");
        verify(payslipRepo, never()).save(any());
        verify(payRunRepo, never()).save(any());
    }

    @Test
    void aRunWithNoEmployeesIsNotCalculated() {
        PayRun run = draftRun();
        when(payRunRepo.findByTenantAndId(TENANT.getValue(), runId)).thenReturn(Optional.of(run));
        when(employeeRepo.findActiveUngrouped(run.getPayClientId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.processPayRun(TENANT, runId))
                .isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("no active employees");
        assertThat(run.getStatus()).isEqualTo("DRAFT");
        verify(engine, never()).requireRules(anyInt());
        verify(payRunRepo, never()).save(any());
    }

    @Test
    void aRunOverlappingAnotherRunOfTheSameGroupIsNotCalculated() {
        PayRun run = draftRun();
        PayRun other = PayRun.create(TENANT.getValue(), run.getPayClientId(), "PR00000",
                LocalDate.of(2027, 3, 15), LocalDate.of(2027, 4, 14), LocalDate.of(2027, 4, 14), 2027);
        za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee emp = mock(za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee.class);
        when(emp.getGrossSalary()).thenReturn(new java.math.BigDecimal("20000"));
        when(payRunRepo.findByTenantAndId(TENANT.getValue(), runId)).thenReturn(Optional.of(run));
        when(employeeRepo.findActiveUngrouped(run.getPayClientId())).thenReturn(List.of(emp));
        when(payRunRepo.findOverlapping(any(), any(), any(), any(), any())).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.processPayRun(TENANT, runId))
                .isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("PR00000");
        assertThat(run.getStatus()).isEqualTo("DRAFT");
    }
}
