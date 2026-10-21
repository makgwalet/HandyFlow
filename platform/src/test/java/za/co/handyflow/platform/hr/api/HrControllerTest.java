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
import za.co.handyflow.platform.hr.application.internal.EmployeeAuditService;
import za.co.handyflow.platform.hr.application.internal.EmployeeChangeService;
import za.co.handyflow.platform.hr.application.internal.EmployeeViewService;
import za.co.handyflow.platform.hr.application.internal.MedicalSchemeService;
import za.co.handyflow.platform.hr.application.internal.PayrollControlService;
import za.co.handyflow.platform.hr.application.internal.PayrollImpactService;
import za.co.handyflow.platform.hr.application.internal.StatutoryRulesService;
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
    @MockitoBean StatutoryRulesService  statutoryRulesService;
    @MockitoBean MedicalSchemeService   medicalSchemeService;
    @MockitoBean PayrollImpactService   payrollImpactService;
    @MockitoBean PayrollControlService  payrollControlService;
    @MockitoBean EmployeeViewService    employeeViewService;
    @MockitoBean EmployeeChangeService  employeeChangeService;
    @MockitoBean EmployeeAuditService   employeeAuditService;
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

    EmployeeViewResponse viewResponse(UUID id, String status) {
        return new EmployeeViewResponse(id, "EMP001", "Jane", "Dlamini", "Jane Dlamini", null, null, "PERMANENT", "Developer",
                "Engineering", LocalDate.of(2026, 1, 1), null, status, null, null, null, false, false,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    EmployeeChangeResponse changeResponse(UUID id) {
        return new EmployeeChangeResponse(id, UUID.randomUUID(), "Jane Dlamini", "COMPENSATION", java.util.List.of(),
                LocalDate.of(2026, 11, 1), "Annual increase", "PENDING", null, Instant.now(), null, null, null, false, null);
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
                BigDecimal.ZERO, BigDecimal.ZERO, 0, null, null, Instant.now(),
                null, null, null, null, null, null, java.util.List.of());
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
        when(employeeViewService.get(any(), eq(id), any(), any())).thenReturn(viewResponse(id, "ACTIVE"));

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
        when(employeeViewService.get(any(), eq(id), any(), any())).thenReturn(viewResponse(id, "ACTIVE"));

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
                        .param("reason", "Resigned")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(hrService, never()).terminateEmployee(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "HR_MANAGE")
    @DisplayName("POST /employees/{id}/terminate with HR_MANAGE returns 200")
    void terminateEmployeeWithHrManageReturns200() throws Exception {
        var id = UUID.randomUUID();
        when(employeeViewService.get(any(), eq(id), any(), any())).thenReturn(viewResponse(id, "TERMINATED"));

        mvc.perform(post(BASE + "/employees/" + id + "/terminate")
                        .param("endDate", "2026-08-20")
                        .param("reason", "Resigned")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TERMINATED"));

        verify(hrService).terminateEmployee(any(), eq(id), eq(LocalDate.of(2026, 8, 20)), eq("Resigned"), any());
    }

    @Test
    @WithMockUser(authorities = "HR_MANAGE")
    @DisplayName("POST /employees/{id}/terminate without a reason is refused before anything is changed")
    void terminateWithoutReasonReturns400() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(post(BASE + "/employees/" + id + "/terminate")
                        .param("endDate", "2026-08-20")
                        .with(csrf()))
                .andExpect(status().is4xxClientError());

        verify(hrService, never()).terminateEmployee(any(), any(), any(), any(), any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Slice 3: who sees what, and who changes pay
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "HR_TEAM_READ")
    @DisplayName("GET /employees with HR_TEAM_READ is allowed, and the view service is told what the caller holds")
    void listWithTeamReadIsAllowed() throws Exception {
        when(employeeViewService.list(any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

        mvc.perform(get(BASE + "/employees")).andExpect(status().isOk());

        verify(employeeViewService).list(any(), any(), any(), any(), argThat(held -> held.contains("HR_TEAM_READ")), any());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_INVOICE_READ")
    @DisplayName("GET /employees/{id} without any HR permission returns 403")
    void getEmployeeWithoutHrPermissionReturns403() throws Exception {
        mvc.perform(get(BASE + "/employees/" + UUID.randomUUID())).andExpect(status().isForbidden());
        verifyNoInteractions(employeeViewService);
    }

    @Test
    @WithMockUser(authorities = "HR_MANAGE")
    @DisplayName("Asking for a pay change needs HR_COMPENSATION_MANAGE; HR_MANAGE alone is refused")
    void requestPayChangeNeedsCompensationManage() throws Exception {
        mvc.perform(post(BASE + "/employees/" + UUID.randomUUID() + "/changes/compensation").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossSalary\":35000,\"effectiveFrom\":\"2026-11-01\",\"reason\":\"Annual increase\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(employeeChangeService);
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "HR_COMPENSATION_MANAGE")
    @DisplayName("Asking for a pay change passes the real user id as the requester")
    void requestPayChangePassesRequester() throws Exception {
        var emp = UUID.randomUUID();
        when(employeeChangeService.requestCompensation(any(), eq(emp), any(), any())).thenReturn(changeResponse(UUID.randomUUID()));

        mvc.perform(post(BASE + "/employees/" + emp + "/changes/compensation").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossSalary\":35000,\"effectiveFrom\":\"2026-11-01\",\"reason\":\"Annual increase\"}"))
                .andExpect(status().isCreated());

        verify(employeeChangeService).requestCompensation(any(), eq(emp), any(), eq(UUID.fromString(USER_ID)));
    }

    @Test
    @WithMockUser(authorities = {"HR_MANAGE", "HR_COMPENSATION_MANAGE"})
    @DisplayName("Deciding on a pay change needs HR_COMPENSATION_APPROVE; managing is not enough")
    void approvePayChangeNeedsApprove() throws Exception {
        mvc.perform(post(BASE + "/employee-changes/" + UUID.randomUUID() + "/approve").with(csrf()))
                .andExpect(status().isForbidden());
        verify(employeeChangeService, never()).approve(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "HR_COMPENSATION_APPROVE")
    @DisplayName("Approving passes the real user id as the decider")
    void approvePayChangePassesDecider() throws Exception {
        var change = UUID.randomUUID();
        when(employeeChangeService.approve(any(), eq(change), any(), any())).thenReturn(changeResponse(change));

        mvc.perform(post(BASE + "/employee-changes/" + change + "/approve").with(csrf())).andExpect(status().isOk());

        verify(employeeChangeService).approve(any(), eq(change), any(), eq(UUID.fromString(USER_ID)));
    }

    @Test
    @WithMockUser(authorities = "HR_READ")
    @DisplayName("Setting a manager needs HR_MANAGE")
    void setManagerNeedsManage() throws Exception {
        mvc.perform(put(BASE + "/employees/" + UUID.randomUUID() + "/manager").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verify(hrService, never()).setManager(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "HR_READ")
    @DisplayName("The employee audit trail is not open to everyone who can read the staff list")
    void auditNeedsMoreThanRead() throws Exception {
        mvc.perform(get(BASE + "/employees/" + UUID.randomUUID() + "/audit")).andExpect(status().isForbidden());
        verifyNoInteractions(employeeAuditService);
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
    @WithMockUser(authorities = "PAYROLL_PREPARE")
    @DisplayName("POST /pay-runs with PAYROLL_PREPARE returns 201")
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

        verify(payrollService, never()).processPayRun(any(), any(), any());
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "PAYROLL_PREPARE")
    @DisplayName("POST /pay-runs/{id}/process with PAYROLL_PREPARE returns 200 and records who calculated it")
    void processPayRunWithPayrollPrepareReturns200() throws Exception {
        var id = UUID.randomUUID();
        when(payrollService.processPayRun(any(), eq(id), eq(UUID.fromString(USER_ID)))).thenReturn(payRunResponse(id));

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

    // ══════════════════════════════════════════════════════════════════════
    // Slice 1: statutory rules, medical scheme and impact report.
    // These endpoints accept PAYROLL_* / HR_MANAGE only; the broad USER_* authorities are rejected on purpose.
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = {"USER_READ", "HR_READ"})
    @DisplayName("GET /statutory/tax-years with only USER_READ and HR_READ returns 403")
    void taxYearsRefuseBroadUserAuthorities() throws Exception {
        mvc.perform(get(BASE + "/statutory/tax-years")).andExpect(status().isForbidden());
        verifyNoInteractions(statutoryRulesService);
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_READ")
    @DisplayName("GET /statutory/tax-years with PAYROLL_READ returns 200")
    void taxYearsWithPayrollReadReturns200() throws Exception {
        when(statutoryRulesService.list()).thenReturn(java.util.List.of());
        mvc.perform(get(BASE + "/statutory/tax-years")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"USER_READ", "HR_READ"})
    @DisplayName("GET /payroll/impact-report without a payroll authority returns 403")
    void impactReportRefusesNonPayrollUsers() throws Exception {
        mvc.perform(get(BASE + "/payroll/impact-report")).andExpect(status().isForbidden());
        verifyNoInteractions(payrollImpactService);
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_READ")
    @DisplayName("GET /payroll/impact-report passes the run id through")
    void impactReportPassesRunId() throws Exception {
        UUID runId = UUID.randomUUID();
        when(payrollImpactService.report(any(), eq(runId)))
                .thenReturn(new PayrollImpactResponse(Instant.now(), java.util.List.of(), 0, java.util.List.of()));
        mvc.perform(get(BASE + "/payroll/impact-report").param("runId", runId.toString())).andExpect(status().isOk());
        verify(payrollImpactService).report(any(), eq(runId));
    }

    @Test
    @WithMockUser(authorities = "USER_READ")
    @DisplayName("POST /employees/{id}/medical-scheme with only USER_READ returns 403")
    void medicalSchemeWriteRefusesUserRead() throws Exception {
        var body = Map.of("schemeName", "Discovery", "dependants", 1, "effectiveFrom", "2026-03-01");
        mvc.perform(post(BASE + "/employees/" + UUID.randomUUID() + "/medical-scheme").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(medicalSchemeService);
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "HR_MANAGE")
    @DisplayName("POST /employees/{id}/medical-scheme with HR_MANAGE returns 201 and records who did it")
    void medicalSchemeWriteRecordsTheUser() throws Exception {
        UUID empId = UUID.randomUUID();
        var body = Map.of("schemeName", "Discovery", "dependants", 1, "effectiveFrom", "2026-03-01");
        when(medicalSchemeService.add(any(), eq(empId), any(), eq(UUID.fromString(USER_ID))))
                .thenReturn(new MedicalSchemeResponse(UUID.randomUUID(), empId, "Discovery", null, 1, 2,
                        LocalDate.of(2026, 3, 1), null, true, null, Instant.now()));
        mvc.perform(post(BASE + "/employees/" + empId + "/medical-scheme").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        verify(medicalSchemeService).add(any(), eq(empId), any(), eq(UUID.fromString(USER_ID)));
    }

    @Test
    @WithMockUser(authorities = "HR_MANAGE")
    @DisplayName("POST /employees/{id}/medical-scheme without a scheme name returns 400")
    void medicalSchemeNeedsAName() throws Exception {
        var body = Map.of("dependants", 1, "effectiveFrom", "2026-03-01");
        mvc.perform(post(BASE + "/employees/" + UUID.randomUUID() + "/medical-scheme").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(medicalSchemeService);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Slice 2 — approving, sending back, finalising, review, settings
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = {"PAYROLL_PREPARE", "PAYROLL_READ", "PAYROLL_STATUTORY_EXPORT"})
    @DisplayName("Preparing and reading do not allow approving or finalising")
    void preparerCannotApproveOrFinalise() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post(BASE + "/pay-runs/" + id + "/approve").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/pay-runs/" + id + "/send-back").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/pay-runs/" + id + "/finalise").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(payrollControlService);
    }

    @Test
    @WithMockUser(authorities = {"USER_UPDATE", "PAYROLL_RUN", "HR_MANAGE"})
    @DisplayName("The old permissions never approve or finalise, whatever the date")
    void legacyPermissionsCannotApprove() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post(BASE + "/pay-runs/" + id + "/approve").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/pay-runs/" + id + "/finalise").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(payrollControlService);
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "PAYROLL_APPROVE")
    @DisplayName("POST /pay-runs/{id}/approve passes the user and the note on")
    void approveRecordsTheUserAndNote() throws Exception {
        UUID id = UUID.randomUUID();
        when(payrollControlService.approve(any(), eq(id), eq(UUID.fromString(USER_ID)), eq("Checked against timesheets")))
                .thenReturn(payRunResponse(id));
        mvc.perform(post(BASE + "/pay-runs/" + id + "/approve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Checked against timesheets\"}"))
                .andExpect(status().isOk());
        verify(payrollControlService).approve(any(), eq(id), eq(UUID.fromString(USER_ID)), eq("Checked against timesheets"));
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "PAYROLL_APPROVE")
    @DisplayName("POST /pay-runs/{id}/approve works without a body")
    void approveWithoutBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(payrollControlService.approve(any(), eq(id), any(), isNull())).thenReturn(payRunResponse(id));
        mvc.perform(post(BASE + "/pay-runs/" + id + "/approve").with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "PAYROLL_APPROVE")
    @DisplayName("POST /pay-runs/{id}/send-back passes the reason on")
    void sendBackPassesTheReason() throws Exception {
        UUID id = UUID.randomUUID();
        when(payrollControlService.sendBack(any(), eq(id), any(), eq("Overtime missing")))
                .thenReturn(payRunResponse(id));
        mvc.perform(post(BASE + "/pay-runs/" + id + "/send-back").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Overtime missing\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "PAYROLL_FINALISE")
    @DisplayName("POST /pay-runs/{id}/finalise with PAYROLL_FINALISE returns 200")
    void finaliseWithPermission() throws Exception {
        UUID id = UUID.randomUUID();
        when(payrollControlService.finalise(any(), eq(id), eq(UUID.fromString(USER_ID)), isNull())).thenReturn(payRunResponse(id));
        mvc.perform(post(BASE + "/pay-runs/" + id + "/finalise").with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_APPROVE")
    @DisplayName("A note longer than 500 characters returns 400")
    void tooLongANote() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post(BASE + "/pay-runs/" + id + "/approve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(payrollControlService);
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_APPROVE")
    @DisplayName("GET /pay-runs/{id}/review and /history are open to an approver")
    void approverSeesReviewAndHistory() throws Exception {
        UUID id = UUID.randomUUID();
        when(payrollControlService.review(any(), eq(id))).thenReturn(
                new PayRunReviewResponse(id, "PR00001", "PREPARED", null, false, 0, 0, java.util.List.of()));
        when(payrollControlService.events(any(), eq(id))).thenReturn(java.util.List.of());
        mvc.perform(get(BASE + "/pay-runs/" + id + "/review")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocking").value(false));
        mvc.perform(get(BASE + "/pay-runs/" + id + "/history")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"USER_READ", "HR_READ"})
    @DisplayName("Review and history are refused to people without a payroll permission")
    void reviewRefusedWithoutPayroll() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get(BASE + "/pay-runs/" + id + "/review")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/pay-runs/" + id + "/history")).andExpect(status().isForbidden());
        verifyNoInteractions(payrollControlService);
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_APPROVE")
    @DisplayName("Changing payroll settings and reading the access review need PAYROLL_CONFIGURATION_ADMIN")
    void settingsAndAccessReviewNeedConfigurationAdmin() throws Exception {
        mvc.perform(put(BASE + "/payroll/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowSelfApproval\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/payroll/access-review")).andExpect(status().isForbidden());
        verifyNoInteractions(payrollControlService);
    }

    @Test
    @WithMockUser(username = USER_ID, authorities = "PAYROLL_CONFIGURATION_ADMIN")
    @DisplayName("PUT /payroll/settings records who changed it, and needs an answer")
    void settingsAreSavedByAConfigurationAdmin() throws Exception {
        when(payrollControlService.updateSettings(any(), eq(true), eq(UUID.fromString(USER_ID))))
                .thenReturn(new PayrollSettingsResponse(true, Instant.now(), LocalDate.of(2026, 12, 31), true));
        mvc.perform(put(BASE + "/payroll/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowSelfApproval\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowSelfApproval").value(true));
        mvc.perform(put(BASE + "/payroll/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_CONFIGURATION_ADMIN")
    @DisplayName("GET /payroll/access-review returns the migration report")
    void accessReviewIsShown() throws Exception {
        when(payrollControlService.accessReview(any()))
                .thenReturn(new PayrollAccessReviewResponse(LocalDate.of(2026, 12, 31), 0, java.util.List.of()));
        mvc.perform(get(BASE + "/payroll/access-review")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rolesNeedingReview").value(0));
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_STATUTORY_EXPORT")
    @DisplayName("EMP201 PDF of a run that is not finalised is refused with 409")
    void emp201PdfWaitsForFinalisation() throws Exception {
        UUID id = UUID.randomUUID(), runId = UUID.randomUUID();
        HrEmp201 e = mock(HrEmp201.class);
        when(e.getPayRunId()).thenReturn(runId);
        HrPayRun run = mock(HrPayRun.class);
        when(run.getStatus()).thenReturn("PREPARED");
        when(emp201Repo.findByTenantAndId(any(), eq(id))).thenReturn(java.util.Optional.of(e));
        when(payRunRepo.findByTenantAndId(any(), eq(runId))).thenReturn(java.util.Optional.of(run));
        mvc.perform(get(BASE + "/emp201/" + id + "/pdf")).andExpect(status().isConflict());
        verifyNoInteractions(emp201PdfGenerator);
    }
}
