package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmployeeStatusChangeServiceTest {

    private static final String WHY = "Safety incident on site, under investigation";

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID asker = UUID.randomUUID(), decider = UUID.randomUUID();

    JdbcTemplate jdbc;
    HrEmployeeRepository employeeRepo;
    EmployeeAuditService audit;
    SeparationOfDutiesChecker duties;
    EmployeeStatusChangeService service;
    HrEmployee emp;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        audit = mock(EmployeeAuditService.class);
        duties = mock(SeparationOfDutiesChecker.class);
        service = new EmployeeStatusChangeService(jdbc, employeeRepo, audit, duties);
        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.now().minusYears(2), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        when(employeeRepo.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
    }

    @SuppressWarnings("unchecked")
    private void lockedRow(EmployeeStatusChangeService.Row row) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.singletonList(row));
    }

    @Test
    void anEmergencySuspensionTakesEffectAtOnceAndNamesAReviewDeadline() {
        var out = service.request(tenant, emp.getId(), "SUSPENDED", WHY, true, asker);

        assertThat(out.applied()).isTrue();
        assertThat(out.emergency()).isTrue();
        assertThat(out.reviewDueAt()).isAfter(java.time.Instant.now());
        assertThat(emp.getStatus()).isEqualTo("SUSPENDED");
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("SUSPENDED_EMERGENCY"), contains("independent review due"), eq(asker));
        verify(duties, never()).recordSoleException(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anOrdinarySuspensionWaitsForAnotherHrManagerAndTheEmployeeIsUntouched() {
        when(duties.otherHolders(any(), eq("HR_MANAGE"), eq(asker))).thenReturn(1L);

        var out = service.request(tenant, emp.getId(), "SUSPENDED", WHY, false, asker);

        assertThat(out.applied()).isFalse();
        assertThat(out.requestId()).isNotNull();
        assertThat(emp.getStatus()).isEqualTo("ACTIVE");
        verify(employeeRepo, never()).save(any());
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("STATUS_SUSPENDED_REQUESTED"), contains(WHY), eq(asker));
    }

    @Test
    void aSoleHrManagerCanActButItIsAnAuditedExceptionWithTheReason() {
        when(duties.otherHolders(any(), eq("HR_MANAGE"), eq(asker))).thenReturn(0L);

        var out = service.request(tenant, emp.getId(), "SUSPENDED", WHY, false, asker);

        assertThat(out.applied()).isTrue();
        assertThat(emp.getStatus()).isEqualTo("SUSPENDED");
        verify(duties).recordSoleException(eq(tenant), eq(asker), eq("HR_MANAGE"), eq("EMPLOYEE_STATUS"), eq(emp.getId()), anyString(),
                argThat(m -> WHY.equals(m.get("reason")) && asker.equals(m.get("initiator")) && asker.equals(m.get("approver"))
                        && "ACTIVE".equals(m.get("from")) && "SUSPENDED".equals(m.get("to"))));
    }

    @Test
    void aSecondOpenRequestForTheSameEmployeeIsRefused() {
        when(duties.otherHolders(any(), eq("HR_MANAGE"), eq(asker))).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThatThrownBy(() -> service.request(tenant, emp.getId(), "SUSPENDED", WHY, false, asker)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMissingReasonOrATerminatedEmployeeIsRefusedBeforeAnythingIsRecorded() {
        assertThatThrownBy(() -> service.request(tenant, emp.getId(), "SUSPENDED", "short", false, asker)).isInstanceOf(IllegalArgumentException.class);
        emp.terminate(LocalDate.now());
        assertThatThrownBy(() -> service.request(tenant, emp.getId(), "SUSPENDED", WHY, true, asker)).isInstanceOf(IllegalArgumentException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void anotherHrManagerApprovesAndTheChangeTakesEffect() {
        lockedRow(new EmployeeStatusChangeService.Row(emp.getId(), "SUSPENDED", WHY, false, "PENDING", asker, null));

        service.approve(tenant, UUID.randomUUID(), "agreed", decider);

        verify(duties).require(eq(tenant), eq("HR_MANAGE"), eq(asker), eq(decider), anyString(), eq("EMPLOYEE_STATUS"), eq(emp.getId()), anyString());
        assertThat(emp.getStatus()).isEqualTo("SUSPENDED");
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("STATUS_SUSPENDED_APPROVED"), anyString(), eq(decider));
    }

    @Test
    void youCannotApproveYourOwnRequestWhenSomeoneElseCould() {
        lockedRow(new EmployeeStatusChangeService.Row(emp.getId(), "SUSPENDED", WHY, false, "PENDING", asker, null));
        doThrow(new HandyFlowException("no", HttpStatus.CONFLICT, "SAME_PERSON")).when(duties)
                .require(any(), any(), any(), any(), anyString(), any(), any(), anyString());

        assertThatThrownBy(() -> service.approve(tenant, UUID.randomUUID(), null, asker)).isInstanceOf(HandyFlowException.class);
        assertThat(emp.getStatus()).isEqualTo("ACTIVE");
        verify(employeeRepo, never()).save(any());
    }

    @Test
    void aRequestThatWasAlreadyDecidedCannotBeApprovedAgain() {
        lockedRow(new EmployeeStatusChangeService.Row(emp.getId(), "SUSPENDED", WHY, false, "APPLIED", asker, null));

        assertThatThrownBy(() -> service.approve(tenant, UUID.randomUUID(), null, decider)).isInstanceOf(HandyFlowException.class);
        assertThat(emp.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void anotherHrManagerCanConfirmAnEmergencySuspension() {
        emp.suspend();
        UUID id = UUID.randomUUID();
        lockedRow(new EmployeeStatusChangeService.Row(emp.getId(), "SUSPENDED", WHY, true, "APPLIED", asker, null));

        service.review(tenant, id, true, "Confirmed after speaking to the site manager", decider);

        assertThat(emp.getStatus()).isEqualTo("SUSPENDED");
        verify(duties).require(eq(tenant), eq("HR_MANAGE"), eq(asker), eq(decider), anyString(), eq("EMPLOYEE_STATUS_REVIEW"), eq(emp.getId()), anyString());
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("EMERGENCY_SUSPENSION_CONFIRMED"), anyString(), eq(decider));
    }

    @Test
    void reversingAnEmergencySuspensionReinstatesTheEmployee() {
        emp.suspend();
        lockedRow(new EmployeeStatusChangeService.Row(emp.getId(), "SUSPENDED", WHY, true, "APPLIED", asker, null));

        service.review(tenant, UUID.randomUUID(), false, "No safety risk found, suspension not justified", decider);

        assertThat(emp.getStatus()).isEqualTo("ACTIVE");
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("EMERGENCY_SUSPENSION_REVERSED"), anyString(), eq(decider));
    }

    @Test
    void thePersonWhoSuspendedCannotReviewItWhenSomeoneElseCould() {
        emp.suspend();
        lockedRow(new EmployeeStatusChangeService.Row(emp.getId(), "SUSPENDED", WHY, true, "APPLIED", asker, null));
        doThrow(new HandyFlowException("no", HttpStatus.CONFLICT, "SAME_PERSON")).when(duties)
                .require(any(), any(), any(), any(), anyString(), any(), any(), anyString());

        assertThatThrownBy(() -> service.review(tenant, UUID.randomUUID(), false, "Reversing my own suspension, no risk found", asker))
                .isInstanceOf(HandyFlowException.class);
        assertThat(emp.getStatus()).isEqualTo("SUSPENDED");
        verify(audit, never()).record(any(), any(), startsWith("EMERGENCY_SUSPENSION_"), any(), any());
    }

    @Test
    void aReviewNeedsAWrittenOutcomeAndOnlyAppliesToEmergencySuspensions() {
        assertThatThrownBy(() -> service.review(tenant, UUID.randomUUID(), true, "ok", decider)).isInstanceOf(IllegalArgumentException.class);
        lockedRow(new EmployeeStatusChangeService.Row(emp.getId(), "SUSPENDED", WHY, false, "APPLIED", asker, null));
        assertThatThrownBy(() -> service.review(tenant, UUID.randomUUID(), true, "Written outcome of the review", decider))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAlreadyReviewedSuspensionCannotBeReviewedTwice() {
        lockedRow(new EmployeeStatusChangeService.Row(emp.getId(), "SUSPENDED", WHY, true, "APPLIED", asker, "CONFIRMED"));

        assertThatThrownBy(() -> service.review(tenant, UUID.randomUUID(), false, "Trying to reverse it afterwards", decider))
                .isInstanceOf(HandyFlowException.class);
    }
}
