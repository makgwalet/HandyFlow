package za.co.handyflow.platform.hr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.handyflow.platform.WebMvcTestSecuritySupport;
import za.co.handyflow.platform.hr.application.internal.Emp201PdfGenerator;
import za.co.handyflow.platform.hr.application.internal.HrService;
import za.co.handyflow.platform.hr.application.internal.PayrollService;
import za.co.handyflow.platform.hr.application.internal.PayslipPdfGenerator;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regression coverage for backlog item 3.1 ("Recently-fixed authorization
 * holes"), plus two more endpoints of the identical bug class found while
 * auditing the rest of HrController per the item's own instruction.
 * <p>
 * ORIGINAL BUGS BEING GUARDED AGAINST (all confirmed via inline
 * {@code // FIX} comments in the real HrController before writing this):
 * <ol>
 *   <li>{@code POST /employees} — was gated on {@code USER_READ}, so any
 *       authenticated read-only user could create employees.</li>
 *   <li>{@code POST /employees/{id}/terminate} — same {@code USER_READ} hole.</li>
 *   <li>{@code POST /leave-requests/{id}/approve} — same {@code USER_READ}
 *       hole, <b>plus</b> {@code approverId} was hard-coded {@code null},
 *       silently breaking the leave-approval audit trail.</li>
 *   <li>{@code POST /employees/{id}/disciplinary} — same {@code USER_READ}
 *       hole, plus {@code issuedBy} was hard-coded {@code null} for the
 *       same reason as #3. Not named in the 3.1 write-up but confirmed
 *       present with an identical {@code // FIX} comment while auditing
 *       the rest of the controller, so it gets the same regression lock.</li>
 *   <li>{@code POST /pay-runs} and {@code POST /pay-runs/{id}/process} —
 *       same {@code USER_READ} hole (payroll creation/processing gated on
 *       a read authority). Also found during the audit, also fixed
 *       in-place already; locked here for the same reason as #4.</li>
 * </ol>
 * Every test below either proves the vulnerable authority is now REJECTED
 * (403) or that the correct authority is ACCEPTED, and — for the two
 * approver-audit-trail bugs — that the real user id is actually passed
 * through rather than {@code null}.
 */
@WebMvcTest(HrController.class)
@Import(WebMvcTestSecuritySupport.class)
class HrControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean HrService              hrService;
    @MockitoBean PayrollService         payrollService;
    @MockitoBean PayslipPdfGenerator    payslipPdfGenerator;
    @MockitoBean HrPayslipRepository    payslipRepo;
    @MockitoBean HrEmployeeRepository   employeeRepo;
    @MockitoBean HrPayRunRepository     payRunRepo;
    @MockitoBean HrEmp201Repository     emp201Repo;
    @MockitoBean Emp201PdfGenerator     emp201PdfGenerator;
    @MockitoBean JdbcTemplate           jdbc;

    static final String BASE = "/api/v1/hr";

    // A syntactically valid UUID used as the @WithMockUser username so that
    // HrController#resolveUserId(Principal) — which does
    // UUID.fromString(principal.getName()) — succeeds instead of silently
    // swallowing a parse failure and returning null. That "silently returns
    // null on a bad principal" fallback is exactly the failure mode bug #3
    // and #4 above originally exhibited via a hard-coded null, so the test
    // must supply a real UUID here to actually exercise the fixed path.
    static final String USER_ID = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

    // ── Fixture builders ─────────────────────────────────────────────────────

    EmployeeResponse employeeResponse(UUID id) {
        return new EmployeeResponse(id, "EMP001", "Jane", "Dlamini", "Jane Dlamini",
                null, null, null, null, null, null, null,
                "PERMANENT", "Developer", "Engineering",
                LocalDate.of(2026, 1, 1), null, "ACTIVE",
                new BigDecimal("30000"), "MONTHLY",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, Instant.now());
    }

    LeaveRequestResponse leaveResponse(UUID id) {
        return new LeaveRequestResponse(id, UUID.randomUUID(), "Jane Dlamini",
                "ANNUAL", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                new BigDecimal("3"), "Family trip", "APPROVED", null, Instant.now());
    }

    DisciplinaryResponse disciplinaryResponse(UUID id) {
        return new DisciplinaryResponse(id, UUID.randomUUID(), "Jane Dlamini",
                LocalDate.of(2026, 8, 1), "LATE_ARRIVAL", "Arrived 2 hours late",
                null, null, false, Instant.now());
    }

    PayRunResponse payRunResponse(UUID id) {
        return new PayRunResponse(id, "PR00001",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 25),
                2026, "DRAFT", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, null, null, Instant.now());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Bug #1 — POST /employees (was USER_READ)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "USER_READ")
    @DisplayName("REGRESSION: POST /employees with only USER_READ returns 403 (was exploitable)")
    void createEmployeeWithReadOnlyReturns403() throws Exception {
        var body = Map.of(
                "firstName", "Jane", "lastName", "Dlamini",
                "startDate", "2026-01-01", "grossSalary", 30000);

        mvc.perform(post(BASE + "/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(hrService, never()).createEmployee(any(), any());
    }

    @Test
    @WithMockUser(authorities = "HR_MANAGE")
    @DisplayName("POST /employees with HR_MANAGE returns 201")
    void createEmployeeWithHrManageReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(hrService.createEmployee(any(), any())).thenReturn(employeeResponse(id));

        var body = Map.of(
                "firstName", "Jane", "lastName", "Dlamini",
                "startDate", "2026-01-01", "grossSalary", 30000);

        mvc.perform(post(BASE + "/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fullName").value("Jane Dlamini"));
    }

    @Test
    @WithMockUser(authorities = "USER_UPDATE")
    @DisplayName("POST /employees with USER_UPDATE returns 201 (the non-HR half of hasAnyAuthority)")
    void createEmployeeWithUserUpdateReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(hrService.createEmployee(any(), any())).thenReturn(employeeResponse(id));

        var body = Map.of(
                "firstName", "Jane", "lastName", "Dlamini",
                "startDate", "2026-01-01", "grossSalary", 30000);

        mvc.perform(post(BASE + "/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Bug #2 — POST /employees/{id}/terminate (was USER_READ)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "USER_READ")
    @DisplayName("REGRESSION: POST /employees/{id}/terminate with only USER_READ returns 403 (was exploitable)")
    void terminateEmployeeWithReadOnlyReturns403() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(post(BASE + "/employees/" + id + "/terminate")
                        .param("endDate", "2026-08-20")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(hrService, never()).terminateEmployee(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "HR_MANAGE")
    @DisplayName("POST /employees/{id}/terminate with HR_MANAGE returns 200")
    void terminateEmployeeWithHrManageReturns200() throws Exception {
        var id = UUID.randomUUID();
        var terminated = new EmployeeResponse(id, "EMP001", "Jane", "Dlamini", "Jane Dlamini",
                null, null, null, null, null, null, null,
                "PERMANENT", "Developer", "Engineering",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 20), "TERMINATED",
                new BigDecimal("30000"), "MONTHLY",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, Instant.now());
        when(hrService.terminateEmployee(any(), eq(id), eq(LocalDate.of(2026, 8, 20))))
                .thenReturn(terminated);

        mvc.perform(post(BASE + "/employees/" + id + "/terminate")
                        .param("endDate", "2026-08-20")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TERMINATED"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Bug #3 — POST /leave-requests/{id}/approve
    // (was USER_READ, AND approverId was hard-coded null)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "USER_READ")
    @DisplayName("REGRESSION: POST /leave-requests/{id}/approve with only USER_READ returns 403 (was exploitable)")
    void approveLeaveWithReadOnlyReturns403() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(post(BASE + "/leave-requests/" + id + "/approve").with(csrf()))
                .andExpect(status().isForbidden());

        verify(hrService, never()).approveLeaveRequest(any(), any(), any());
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "HR_MANAGE")
    @DisplayName("REGRESSION: POST /leave-requests/{id}/approve records the real approver id, not null")
    void approveLeaveRecordsRealApproverId() throws Exception {
        var id = UUID.randomUUID();
        when(hrService.approveLeaveRequest(any(), eq(id), any()))
                .thenReturn(leaveResponse(id));

        mvc.perform(post(BASE + "/leave-requests/" + id + "/approve").with(csrf()))
                .andExpect(status().isOk());

        var approverIdCaptor = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(hrService).approveLeaveRequest(any(), eq(id), approverIdCaptor.capture());

        // The original bug hard-coded this argument to null, silently
        // breaking the approval audit trail. This is the assertion that
        // actually catches a regression back to that behaviour.
        Assertions.assertNotNull(approverIdCaptor.getValue(),
                "approverId must not be null — this is exactly the audit-trail bug that was fixed");
        Assertions.assertEquals(UUID.fromString(USER_ID), approverIdCaptor.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Bug #4 (found during audit, same class as #3) —
    // POST /employees/{id}/disciplinary
    // (was USER_READ, AND issuedBy was hard-coded null)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "USER_READ")
    @DisplayName("REGRESSION: POST /employees/{id}/disciplinary with only USER_READ returns 403 (was exploitable)")
    void addDisciplinaryWithReadOnlyReturns403() throws Exception {
        var id = UUID.randomUUID();
        var body = Map.of(
                "incidentDate", "2026-08-01",
                "incidentType", "LATE_ARRIVAL",
                "description", "Arrived 2 hours late");

        mvc.perform(post(BASE + "/employees/" + id + "/disciplinary").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(hrService, never()).addDisciplinary(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "HR_MANAGE")
    @DisplayName("REGRESSION: POST /employees/{id}/disciplinary records the real issuedBy id, not null")
    void addDisciplinaryRecordsRealIssuedBy() throws Exception {
        var id = UUID.randomUUID();
        when(hrService.addDisciplinary(any(), eq(id), any(), any()))
                .thenReturn(disciplinaryResponse(id));

        var body = Map.of(
                "incidentDate", "2026-08-01",
                "incidentType", "LATE_ARRIVAL",
                "description", "Arrived 2 hours late");

        mvc.perform(post(BASE + "/employees/" + id + "/disciplinary").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        var issuedByCaptor = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(hrService).addDisciplinary(any(), eq(id), any(), issuedByCaptor.capture());

        Assertions.assertNotNull(issuedByCaptor.getValue(),
                "issuedBy must not be null — same audit-trail bug class as leave approval");
        Assertions.assertEquals(UUID.fromString(USER_ID), issuedByCaptor.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Bug #5 (found during audit, same class as #1/#2) —
    // POST /pay-runs and POST /pay-runs/{id}/process
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "USER_READ")
    @DisplayName("REGRESSION: POST /pay-runs with only USER_READ returns 403 (was exploitable)")
    void createPayRunWithReadOnlyReturns403() throws Exception {
        var body = Map.of(
                "periodStart", "2026-08-01",
                "periodEnd", "2026-08-31",
                "payDate", "2026-08-25");

        mvc.perform(post(BASE + "/pay-runs").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(payrollService, never()).createPayRun(any(), any());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_RUN")
    @DisplayName("POST /pay-runs with PAYROLL_RUN returns 201")
    void createPayRunWithPayrollRunReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(payrollService.createPayRun(any(), any())).thenReturn(payRunResponse(id));

        var body = Map.of(
                "periodStart", "2026-08-01",
                "periodEnd", "2026-08-31",
                "payDate", "2026-08-25");

        mvc.perform(post(BASE + "/pay-runs").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(authorities = "USER_READ")
    @DisplayName("REGRESSION: POST /pay-runs/{id}/process with only USER_READ returns 403 (was exploitable)")
    void processPayRunWithReadOnlyReturns403() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(post(BASE + "/pay-runs/" + id + "/process").with(csrf()))
                .andExpect(status().isForbidden());

        verify(payrollService, never()).processPayRun(any(), any());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_RUN")
    @DisplayName("POST /pay-runs/{id}/process with PAYROLL_RUN returns 200")
    void processPayRunWithPayrollRunReturns200() throws Exception {
        var id = UUID.randomUUID();
        when(payrollService.processPayRun(any(), eq(id))).thenReturn(payRunResponse(id));

        mvc.perform(post(BASE + "/pay-runs/" + id + "/process").with(csrf()))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Baseline — unauthenticated requests are rejected outright
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /employees without any auth returns 403")
    void createEmployeeWithoutAuthReturns403() throws Exception {
        var body = Map.of(
                "firstName", "Jane", "lastName", "Dlamini",
                "startDate", "2026-01-01", "grossSalary", 30000);

        mvc.perform(post(BASE + "/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }
}