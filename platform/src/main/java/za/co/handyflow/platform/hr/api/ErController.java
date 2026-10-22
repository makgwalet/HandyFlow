package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.*;
import za.co.handyflow.platform.hr.dto.ErDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Employee relations: discipline, grievances and incapacity. HR runs the cases; an employee sees their own under /mine, lodges their own
 * grievance, acknowledges an outcome and appeals. The services check the permission again for each action.
 */
@RestController
@RequestMapping("/api/v1/hr/employee-relations")
@RequiredArgsConstructor
@Tag(name = "Employee relations", description = "Discipline, grievances and incapacity cases")
public class ErController {

    private static final String SIGNED_IN = "isAuthenticated()";
    private static final String READ = "hasAnyAuthority('HR_ER_READ','HR_ER_MANAGE','HR_ER_DECIDE')";
    private static final String MANAGE = "hasAnyAuthority('HR_ER_MANAGE')";
    private static final String DECIDE = "hasAnyAuthority('HR_ER_DECIDE')";

    private final ErCaseService cases;
    private final DisciplineService discipline;
    private final GrievanceService grievances;
    private final IncapacityService incapacity;
    private final ErPolicyService policy;
    private final ErTerminationService termination;

    // ── For HR ────────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/cases")
    @PreAuthorize(READ)
    @Operation(summary = "Cases, optionally by type and status (OPEN for everything not closed or withdrawn)")
    public ResponseEntity<ApiResponse<List<Row>>> list(@RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                                       Authentication auth) {
        return ok(cases.list(tenant(), type, status, HrAccess.held(auth), user(auth)));
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<Detail>> get(@PathVariable UUID id, Authentication auth) {
        return ok(cases.get(tenant(), id, HrAccess.held(auth), user(auth)));
    }

    @PostMapping("/cases")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> open(@RequestBody OpenRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Case opened", cases.open(tenant(), req, user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/cases/{id}/investigate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> investigate(@PathVariable UUID id, Authentication auth) {
        return ok(cases.investigate(tenant(), id, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/assign")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> assign(@PathVariable UUID id, @RequestBody AssignRequest req, Authentication auth) {
        return ok(cases.assign(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/parties")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> parties(@PathVariable UUID id, @RequestBody PartiesRequest req, Authentication auth) {
        return ok(cases.setParties(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/confidential")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> confidential(@PathVariable UUID id, @RequestBody FlagRequest req, Authentication auth) {
        return ok(cases.setConfidential(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/notice")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Give notice of a hearing (discipline, with the charges) or a meeting (incapacity). Again to postpone or correct")
    public ResponseEntity<ApiResponse<Detail>> notice(@PathVariable UUID id, @RequestBody NoticeRequest req, Authentication auth) {
        return ok(cases.issueNotice(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/hearing")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> hearing(@PathVariable UUID id, @RequestBody HearingRequest req, Authentication auth) {
        return ok(cases.recordHearing(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/informal")
    @PreAuthorize(DECIDE)
    @Operation(summary = "Counselling, verbal or written warning after the employee has had the chance to respond, without a hearing")
    public ResponseEntity<ApiResponse<Detail>> informal(@PathVariable UUID id, @RequestBody InformalRequest req, Authentication auth) {
        return ok(discipline.informal(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/decision")
    @PreAuthorize(DECIDE)
    @Operation(summary = "Findings on every charge and the sanction, after the hearing")
    public ResponseEntity<ApiResponse<Detail>> decision(@PathVariable UUID id, @RequestBody DecisionRequest req, Authentication auth) {
        return ok(discipline.decide(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/grievance-decision")
    @PreAuthorize(DECIDE)
    public ResponseEntity<ApiResponse<Detail>> grievanceDecision(@PathVariable UUID id, @RequestBody GrievanceDecisionRequest req, Authentication auth) {
        return ok(grievances.decide(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/incapacity-decision")
    @PreAuthorize(DECIDE)
    public ResponseEntity<ApiResponse<Detail>> incapacityDecision(@PathVariable UUID id, @RequestBody IncapacityDecisionRequest req, Authentication auth) {
        return ok(incapacity.decide(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/communicate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> communicate(@PathVariable UUID id, @RequestBody CommunicateRequest req, Authentication auth) {
        return ok(cases.communicate(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/appeal")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Lodge an appeal (or take a grievance to the next level) for the employee. Late ones need acceptLate and the decide permission")
    public ResponseEntity<ApiResponse<Detail>> appeal(@PathVariable UUID id, @RequestBody AppealRequest req, Authentication auth) {
        return ok(cases.lodgeAppeal(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/appeal-decision")
    @PreAuthorize(DECIDE)
    public ResponseEntity<ApiResponse<Detail>> appealDecision(@PathVariable UUID id, @RequestBody AppealDecisionRequest req, Authentication auth) {
        return ok(appealDecider(id, req, auth));
    }

    @PostMapping("/cases/{id}/grievance-escalation-decision")
    @PreAuthorize(DECIDE)
    public ResponseEntity<ApiResponse<Detail>> escalationDecision(@PathVariable UUID id, @RequestBody GrievanceDecisionRequest req, Authentication auth) {
        return ok(grievances.decideEscalation(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/close")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> close(@PathVariable UUID id, @RequestBody ReasonRequest req, Authentication auth) {
        return ok(cases.close(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> withdraw(@PathVariable UUID id, @RequestBody ReasonRequest req, Authentication auth) {
        return ok(cases.withdraw(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/suspend")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Precautionary suspension on full pay. Does not change the employee's status, so payroll is unaffected")
    public ResponseEntity<ApiResponse<Detail>> suspend(@PathVariable UUID id, @RequestBody SuspendRequest req, Authentication auth) {
        return ok(cases.suspend(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/suspension/end")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> endSuspension(@PathVariable UUID id, @RequestBody(required = false) DateRequest req, Authentication auth) {
        return ok(cases.endSuspension(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/end-employment")
    @PreAuthorize(DECIDE)
    @Operation(summary = "End the employee's employment after a dismissal that was communicated and not under appeal. The next pay run pays the final pay")
    public ResponseEntity<ApiResponse<Detail>> endEmployment(@PathVariable UUID id, @RequestBody EndEmploymentRequest req, Authentication auth) {
        return ok(termination.endEmployment(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/warning-validity")
    @PreAuthorize(DECIDE)
    @Operation(summary = "Say how long a warning recorded before validity existed stays in force")
    public ResponseEntity<ApiResponse<Detail>> warningValidity(@PathVariable UUID id, @RequestBody ValidityRequest req, Authentication auth) {
        return ok(cases.setWarningValidity(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/steps")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> step(@PathVariable UUID id, @RequestBody StepRequest req, Authentication auth) {
        return ok(incapacity.step(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/review")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> review(@PathVariable UUID id, @RequestBody ReviewRequest req, Authentication auth) {
        return ok(incapacity.review(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cases/{id}/return-to-investigation")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> returnToInvestigation(@PathVariable UUID id, @RequestBody ReasonRequest req, Authentication auth) {
        return ok(incapacity.returnToInvestigation(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @GetMapping("/employees/{employeeId}/warnings")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WarningView>>> warnings(@PathVariable UUID employeeId, Authentication auth) {
        return ok(cases.warnings(tenant(), employeeId, HrAccess.held(auth), user(auth)));
    }

    @GetMapping("/policy")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<PolicyView>> getPolicy() {
        var p = policy.get(tenant());
        return ok(new PolicyView(p.noticeHours(), p.verbalMonths(), p.writtenMonths(), p.finalMonths(), p.appealDays(), p.grievanceResponseDays(), p.minReviewDays()));
    }

    @PutMapping("/policy")
    @PreAuthorize(DECIDE)
    @Operation(summary = "Company settings the law leaves open: notice hours, warning validity, appeal and grievance times")
    public ResponseEntity<ApiResponse<PolicyView>> savePolicy(@RequestBody PolicyRequest req, Authentication auth) {
        var p = policy.save(tenant(), req, user(auth), HrAccess.held(auth));
        return ok(new PolicyView(p.noticeHours(), p.verbalMonths(), p.writtenMonths(), p.finalMonths(), p.appealDays(), p.grievanceResponseDays(), p.minReviewDays()));
    }

    // ── For the employee ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/mine")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<List<Mine>>> mine(Authentication auth) {
        return ok(cases.mine(tenant(), user(auth)));
    }

    @GetMapping("/mine/{id}")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<Mine>> mineOne(@PathVariable UUID id, Authentication auth) {
        return ok(cases.mineOne(tenant(), id, user(auth)));
    }

    @PostMapping("/mine/grievances")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "An employee lodges their own grievance")
    public ResponseEntity<ApiResponse<Mine>> lodge(@RequestBody LodgeGrievanceRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Grievance lodged", cases.lodgeGrievance(tenant(), req, user(auth))));
    }

    @PostMapping("/mine/{id}/acknowledge")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Confirm the outcome was received. Receiving is not agreeing: the right to appeal remains")
    public ResponseEntity<ApiResponse<Mine>> acknowledge(@PathVariable UUID id, Authentication auth) {
        return ok(cases.acknowledge(tenant(), id, user(auth)));
    }

    @PostMapping("/mine/{id}/appeal")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<Mine>> appealOwn(@PathVariable UUID id, @RequestBody AppealRequest req, Authentication auth) {
        return ok(cases.appealOwn(tenant(), id, req, user(auth)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────────

    /** One endpoint for appeal decisions: the case type decides which service applies. */
    private Detail appealDecider(UUID id, AppealDecisionRequest req, Authentication auth) {
        var held = HrAccess.held(auth);
        Detail d = cases.get(tenant(), id, held, user(auth));
        if (za.co.handyflow.platform.hr.domain.model.ErRules.INCAPACITY.equals(d.type())) return incapacity.decideAppeal(tenant(), id, req, user(auth), held);
        return discipline.decideAppeal(tenant(), id, req, user(auth), held);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok(ApiResponse.success(body)); }

    private static za.co.handyflow.platform.shared.TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    private static UUID user(Authentication auth) {
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}
