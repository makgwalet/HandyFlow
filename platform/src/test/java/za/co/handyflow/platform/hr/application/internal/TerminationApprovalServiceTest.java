package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TerminationApprovalServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();

    JdbcTemplate jdbc;
    HrService hrService;
    HrEmployeeRepository employeeRepo;
    EmployeeAuditService audit;
    SeparationOfDutiesChecker duties;
    TerminationApprovalService service;
    HrEmployee emp;
    final LocalDate lastDay = LocalDate.now().minusDays(1);
    final HrService.Terminated terminated = new HrService.Terminated(null, List.of());

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        hrService = mock(HrService.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        audit = mock(EmployeeAuditService.class);
        duties = mock(SeparationOfDutiesChecker.class);
        service = new TerminationApprovalService(jdbc, hrService, employeeRepo, audit, duties);
        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.now().minusYears(2), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        when(employeeRepo.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(hrService.terminateEmployee(any(), eq(emp.getId()), any(), any(), any(), any(), any())).thenReturn(terminated);
    }

    @Test
    void soleHrManagerEndsEmploymentAtOnceAndItIsRecorded() {
        when(duties.otherHolders(any(), eq("HR_MANAGE"), eq(actor))).thenReturn(0L);

        var out = service.request(tenant, emp.getId(), lastDay, "Resigned", null, null, actor);

        assertThat(out.pending()).isFalse();
        assertThat(out.done()).isSameAs(terminated);
        verify(duties).recordSoleException(eq(tenant), eq(actor), eq("HR_MANAGE"), eq("TERMINATION"), eq(emp.getId()), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void whenAnotherHrManagerExistsTheRequestWaitsAndTheEmployeeIsUntouched() {
        when(duties.otherHolders(any(), eq("HR_MANAGE"), eq(actor))).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        var out = service.request(tenant, emp.getId(), lastDay, "Resigned", null, null, actor);

        assertThat(out.pending()).isTrue();
        assertThat(out.requestId()).isNotNull();
        verify(hrService, never()).terminateEmployee(any(), any(), any(), any(), any(), any(), any());
        verify(duties, never()).recordSoleException(any(), any(), any(), any(), any(), any());
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("TERMINATION_REQUESTED"), any(), eq(actor));
    }

    @Test
    void secondRequestForTheSameEmployeeIsRefused() {
        when(duties.otherHolders(any(), eq("HR_MANAGE"), eq(actor))).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThatThrownBy(() -> service.request(tenant, emp.getId(), lastDay, "Resigned", null, null, actor))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTerminationWithNoReasonIsRefusedBeforeAnythingIsAsked() {
        assertThatThrownBy(() -> service.request(tenant, emp.getId(), lastDay, " ", null, null, actor))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(duties);
    }

    @Test
    void aLastDayInTheFutureIsRefused() {
        assertThatThrownBy(() -> service.request(tenant, emp.getId(), LocalDate.now().plusDays(2), "Resigned", null, null, actor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decliningNeedsAReason() {
        assertThatThrownBy(() -> service.reject(tenant, UUID.randomUUID(), " ", actor)).isInstanceOf(IllegalArgumentException.class);
    }
}
