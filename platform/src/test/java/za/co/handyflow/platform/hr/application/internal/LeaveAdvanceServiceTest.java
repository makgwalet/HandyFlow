package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.PayAdjustmentRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Leave taken in advance: shown for people who have left, and taken back only with a named, dated written agreement. */
class LeaveAdvanceServiceTest {

    final TenantId tenant = TenantId.of(UUID.randomUUID());
    final UUID actor = UUID.randomUUID();
    final LocalDate last = LocalDate.of(2026, 9, 30);
    HrEmployeeRepository employees;
    LeaveService leave;
    PayAdjustmentService pay;
    LeaveAdvanceService service;
    HrEmployee emp;

    @BeforeEach
    void setUp() {
        employees = mock(HrEmployeeRepository.class);
        leave = mock(LeaveService.class);
        pay = mock(PayAdjustmentService.class);
        service = new LeaveAdvanceService(employees, leave, pay);
        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 3, 1), "PERMANENT", new BigDecimal("26000"), "MONTHLY");
        when(employees.findActiveById(tenant, emp.getId())).thenReturn(Optional.of(emp));
        when(pay.forEmployee(any(), any())).thenReturn(List.of());
    }

    private static PayAdjustmentDtos.View entered(String status) {
        return new PayAdjustmentDtos.View(UUID.randomUUID(), UUID.randomUUID(), "EMP-1", "Jane Dlamini", PayAdjustmentRules.RECOVERY, "Recovery",
                new BigDecimal("1000"), LocalDate.of(2026, 9, 30), "Leave taken in advance", null, null, "r", status, null, null, true, null, Instant.now());
    }

    @Test
    void anEmployeeStillWorkingHasNothingToShow() {
        var p = service.position(tenant, emp.getId());
        assertThat(p.terminated()).isFalse();
        assertThat(p.days()).isEqualByComparingTo("0");
        verifyNoInteractions(leave);
    }

    @Test
    void aLeaverShowsDaysAmountAndWhetherARecoveryIsEntered() {
        emp.terminate(last, "Resigned", actor);
        when(leave.annualLeaveAdvance(tenant, emp, last)).thenReturn(new BigDecimal("2.5"));
        when(pay.forEmployee(tenant, emp.getId())).thenReturn(List.of(entered("OPEN")));

        var p = service.position(tenant, emp.getId());

        assertThat(p.terminated()).isTrue();
        assertThat(p.days()).isEqualByComparingTo("2.5");
        assertThat(p.amount().signum()).isPositive();
        assertThat(p.alreadyEntered()).isTrue();
    }

    @Test
    void aCancelledRecoveryDoesNotCount() {
        emp.terminate(last, "Resigned", actor);
        when(leave.annualLeaveAdvance(tenant, emp, last)).thenReturn(new BigDecimal("1"));
        when(pay.forEmployee(tenant, emp.getId())).thenReturn(List.of(entered("CANCELLED")));
        assertThat(service.position(tenant, emp.getId()).alreadyEntered()).isFalse();
    }

    @Test
    void recoveringWithAnAgreementEntersARecoveryDatedOnTheLastDay() {
        emp.terminate(last, "Resigned", actor);
        when(leave.annualLeaveAdvance(tenant, emp, last)).thenReturn(new BigDecimal("2"));

        service.recover(tenant, emp.getId(), new LeaveAdvanceService.RecoverRequest("Leave letter", LocalDate.of(2026, 8, 1)), actor);

        ArgumentCaptor<PayAdjustmentDtos.AddRequest> req = ArgumentCaptor.forClass(PayAdjustmentDtos.AddRequest.class);
        verify(pay).add(eq(tenant), eq(emp.getId()), req.capture(), eq(actor));
        assertThat(req.getValue().kind()).isEqualTo(PayAdjustmentRules.RECOVERY);
        assertThat(req.getValue().payFrom()).isEqualTo(last);
        assertThat(req.getValue().amount().signum()).isPositive();
    }

    @Test
    void withoutAnAgreementNothingIsEntered() {
        emp.terminate(last, "Resigned", actor);
        when(leave.annualLeaveAdvance(tenant, emp, last)).thenReturn(new BigDecimal("2"));
        assertThatThrownBy(() -> service.recover(tenant, emp.getId(), new LeaveAdvanceService.RecoverRequest(" ", LocalDate.of(2026, 8, 1)), actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recover(tenant, emp.getId(), null, actor)).isInstanceOf(IllegalArgumentException.class);
        verify(pay, never()).add(any(), any(), any(), any());
    }

    @Test
    void noDaysTakenInAdvanceMeansNothingToRecover() {
        emp.terminate(last, "Resigned", actor);
        when(leave.annualLeaveAdvance(tenant, emp, last)).thenReturn(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.recover(tenant, emp.getId(), new LeaveAdvanceService.RecoverRequest("Letter", LocalDate.of(2026, 8, 1)), actor))
                .isInstanceOf(IllegalArgumentException.class);
        verify(pay, never()).add(any(), any(), any(), any());
    }
}
