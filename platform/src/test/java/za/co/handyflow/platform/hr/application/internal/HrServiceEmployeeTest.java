package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.CreateEmployeeRequest;
import za.co.handyflow.platform.hr.dto.UpdateEmployeeRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Employee master rules in HrService: what is checked on the way in, what an edit may touch, termination and reporting lines. */
class HrServiceEmployeeTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();

    HrEmployeeRepository repo;
    EmployeeAuditService audit;
    EmployeeNumberGenerator numberGen;
    ApplicationEventPublisher events;
    HrService service;
    HrEmployee emp;

    @BeforeEach
    void setUp() {
        repo = mock(HrEmployeeRepository.class);
        audit = mock(EmployeeAuditService.class);
        numberGen = mock(EmployeeNumberGenerator.class);
        events = mock(ApplicationEventPublisher.class);
        service = new HrService(repo, mock(HrDisciplinaryRepository.class), numberGen, events, audit, mock(LeaveService.class));

        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        emp.setBankAccountNumber("62000011112222");
        when(repo.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(numberGen.next(any())).thenReturn("EMP-9");
        when(repo.countByIdNumber(any(), any(), any())).thenReturn(0L);
    }

    private CreateEmployeeRequest create(String id, LocalDate dob, String bankAccount) {
        return new CreateEmployeeRequest("Sipho", "Khumalo", id, null, dob, null, null, "sipho@example.com", "0821234567",
                LocalDate.of(2026, 1, 1), "PERMANENT", "Clerk", "Admin", new BigDecimal("20000"), "MONTHLY",
                "FNB", bankAccount, "250655", null, null, null, null, null, null);
    }

    private UpdateEmployeeRequest edit(String idNumber, String jobTitle) {
        return new UpdateEmployeeRequest(null, null, null, jobTitle, null, null, null, null, idNumber, null, null, null, null);
    }

    // ── Creating ────────────────────────────────────────────────────────────────

    @Test
    void createRefusesAnIdNumberWithATypingMistake() {
        assertThatThrownBy(() -> service.createEmployee(tenant, create("8001015009088", null, "62000011112222")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("valid South African ID");
        verify(repo, never()).save(any());
    }

    @Test
    void createRefusesADateOfBirthThatDisagreesWithTheId() {
        assertThatThrownBy(() -> service.createEmployee(tenant, create("8001015009087", LocalDate.of(1985, 5, 5), "62000011112222")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not match");
    }

    @Test
    void createRefusesADuplicateIdNumber() {
        when(repo.countByIdNumber(any(), eq("8001015009087"), any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createEmployee(tenant, create("8001015009087", null, "62000011112222")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
    }

    @Test
    void createRefusesABankAccountThatIsNotDigits() {
        assertThatThrownBy(() -> service.createEmployee(tenant, create("8001015009087", null, "ABC")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAcceptsAValidEmployeeAndWritesTheAuditTrail() {
        var created = service.createEmployee(tenant, create("8001015009087", null, "62000011112222"));

        assertThat(created.firstName()).isEqualTo("Sipho");
        verify(audit).record(eq(tenant), any(), eq("CREATED"), any(), any());
    }

    // ── Editing ─────────────────────────────────────────────────────────────────

    @Test
    void editNeverTouchesPayOrBank() {
        service.updateDetails(tenant, emp.getId(), edit(null, "Senior Developer"), true, actor);

        assertThat(emp.getJobTitle()).isEqualTo("Senior Developer");
        assertThat(emp.getGrossSalary()).isEqualByComparingTo("30000");
        assertThat(emp.getBankAccountNumber()).isEqualTo("62000011112222");
    }

    @Test
    void legacyUpdateFromAFullRequestDoesNotOverwritePay() {
        service.updateEmployee(tenant, emp.getId(), create("8001015009087", null, "99999999"));

        assertThat(emp.getGrossSalary()).isEqualByComparingTo("30000");
        assertThat(emp.getBankAccountNumber()).isEqualTo("62000011112222");
        assertThat(emp.getIdNumber()).isNull();
    }

    @Test
    void personalDataIsIgnoredWithoutThePermission() {
        service.updateDetails(tenant, emp.getId(), edit("8001015009087", null), false, actor);

        assertThat(emp.getIdNumber()).isNull();
    }

    @Test
    void personalDataIsAppliedWithThePermissionAndIsChecked() {
        assertThatThrownBy(() -> service.updateDetails(tenant, emp.getId(), edit("8001015009088", null), true, actor))
                .isInstanceOf(IllegalArgumentException.class);

        service.updateDetails(tenant, emp.getId(), edit("8001015009087", null), true, actor);
        assertThat(emp.getIdNumber()).isEqualTo("8001015009087");
    }

    @Test
    void auditNamesTheFieldsChangedButNotTheirValues() {
        service.updateDetails(tenant, emp.getId(), edit("8001015009087", "Senior Developer"), true, actor);

        verify(audit).record(eq(tenant), eq(emp.getId()), eq("UPDATED"),
                argThat(d -> d.contains("job title") && d.contains("ID number") && !d.contains("8001015009087")), eq(actor));
    }

    // ── Terminating ─────────────────────────────────────────────────────────────

    @Test
    void terminationKeepsTheReasonAndWhoRecordedIt() {
        service.terminateEmployee(tenant, emp.getId(), LocalDate.now().minusDays(1), "Resigned", actor);

        assertThat(emp.getStatus()).isEqualTo("TERMINATED");
        assertThat(emp.getTerminationReason()).isEqualTo("Resigned");
        assertThat(emp.getTerminatedBy()).isEqualTo(actor);
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("TERMINATED"), any(), eq(actor));
    }

    @Test
    void terminationWithoutAReasonIsRefused() {
        assertThatThrownBy(() -> service.terminateEmployee(tenant, emp.getId(), LocalDate.now().minusDays(1), " ", actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(emp.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void terminationInTheFutureIsRefused() {
        assertThatThrownBy(() -> service.terminateEmployee(tenant, emp.getId(), LocalDate.now().plusDays(3), "Resigned", actor))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
    }

    // ── Reporting lines and login ───────────────────────────────────────────────

    @Test
    void managerMustBeACurrentEmployeeAndCannotCreateALoop() {
        HrEmployee boss = HrEmployee.create(tenant, "EMP-2", "Boss", "Mokoena", LocalDate.of(2020, 1, 1), "PERMANENT", new BigDecimal("50000"), "MONTHLY");
        boss.setManagerId(emp.getId());
        when(repo.findNotTerminated(any())).thenReturn(List.of(emp, boss));

        assertThatThrownBy(() -> service.setManager(tenant, emp.getId(), boss.getId(), actor)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setManager(tenant, emp.getId(), UUID.randomUUID(), actor)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setManager(tenant, emp.getId(), emp.getId(), actor)).isInstanceOf(IllegalArgumentException.class);
        assertThat(emp.getManagerId()).isNull();
    }

    @Test
    void managerIsSetAndRecorded() {
        HrEmployee boss = HrEmployee.create(tenant, "EMP-2", "Boss", "Mokoena", LocalDate.of(2020, 1, 1), "PERMANENT", new BigDecimal("50000"), "MONTHLY");
        when(repo.findNotTerminated(any())).thenReturn(List.of(emp, boss));

        service.setManager(tenant, emp.getId(), boss.getId(), actor);

        assertThat(emp.getManagerId()).isEqualTo(boss.getId());
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("MANAGER_CHANGED"), eq("Boss Mokoena"), eq(actor));
    }

    @Test
    void aLoginCanBeLinkedToOnlyOneEmployee() {
        UUID login = UUID.randomUUID();
        HrEmployee other = HrEmployee.create(tenant, "EMP-3", "Other", "Naidoo", LocalDate.of(2020, 1, 1), "PERMANENT", new BigDecimal("50000"), "MONTHLY");
        when(repo.findByUserId(any(), eq(login))).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.linkUser(tenant, emp.getId(), login, actor))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Other Naidoo");
        assertThat(emp.getUserId()).isNull();
    }
}
