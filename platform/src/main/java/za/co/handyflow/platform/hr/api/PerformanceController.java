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
import za.co.handyflow.platform.hr.dto.PerfDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Performance: review cycles, reviews, goals, 360 feedback and improvement plans. Employees and managers work on their own and their team's
 * records without any special permission; HR_PERF_READ and HR_PERF_MANAGE open the company-wide views and the HR steps. The services check
 * who the person is in each case.
 */
@RestController
@RequestMapping("/api/v1/hr/performance")
@RequiredArgsConstructor
@Tag(name = "HR performance", description = "Review cycles, reviews, goals, 360 feedback and improvement plans")
public class PerformanceController {

    private static final String SIGNED_IN = "isAuthenticated()";
    private static final String READ = "hasAnyAuthority('HR_PERF_READ','HR_PERF_MANAGE')";
    private static final String MANAGE = "hasAuthority('HR_PERF_MANAGE')";

    private final PerfCycleService cycles;
    private final PerfReviewService reviews;
    private final PerfGoalService goals;
    private final PerfFeedbackService feedback;
    private final PerfPlanService plans;

    @GetMapping("/me")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Who the signed-in person is in the reporting lines, and what they hold")
    public ResponseEntity<ApiResponse<Me>> me(Authentication auth) {
        return ok(reviews.me(tenant(), user(auth), HrAccess.held(auth)));
    }

    // ── Cycles (HR) ───────────────────────────────────────────────────────────────────────────────

    @GetMapping("/cycles")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<CycleRow>>> cycles(Authentication auth) { return ok(cycles.cycles(tenant(), HrAccess.held(auth))); }

    @GetMapping("/cycles/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<CycleDetail>> cycle(@PathVariable UUID id, Authentication auth) {
        return ok(cycles.cycle(tenant(), id, HrAccess.held(auth), user(auth)));
    }

    @PostMapping("/cycles")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CycleDetail>> createCycle(@RequestBody CycleRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Cycle created", cycles.create(tenant(), req, user(auth), HrAccess.held(auth))));
    }

    @PutMapping("/cycles/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CycleDetail>> updateCycle(@PathVariable UUID id, @RequestBody CycleRequest req, Authentication auth) {
        return ok(cycles.update(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @DeleteMapping("/cycles/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Void>> deleteCycle(@PathVariable UUID id, Authentication auth) {
        cycles.delete(tenant(), id, HrAccess.held(auth));
        return ResponseEntity.ok(ApiResponse.success("Cycle deleted", null));
    }

    @PostMapping("/cycles/{id}/launch")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Open the cycle and make a review for everyone who worked in the period")
    public ResponseEntity<ApiResponse<CycleDetail>> launch(@PathVariable UUID id, Authentication auth) {
        return ok(cycles.launch(tenant(), id, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cycles/{id}/reviews")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CycleDetail>> addReview(@PathVariable UUID id, @RequestBody AddReviewRequest req, Authentication auth) {
        return ok(cycles.addReview(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/cycles/{id}/close")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CycleDetail>> closeCycle(@PathVariable UUID id, Authentication auth) {
        return ok(cycles.close(tenant(), id, user(auth), HrAccess.held(auth)));
    }

    // ── Reviews ───────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/reviews")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Reviews about me (scope=mine), that I am the reviewer of (team), or all in a cycle (all, HR_PERF_READ)")
    public ResponseEntity<ApiResponse<List<ReviewRow>>> reviews(@RequestParam(defaultValue = "mine") String scope, @RequestParam(required = false) UUID cycleId, Authentication auth) {
        return ok(reviews.reviews(tenant(), cycleId, scope, HrAccess.held(auth), user(auth)));
    }

    @GetMapping("/reviews/{id}")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<ReviewDetail>> review(@PathVariable UUID id, Authentication auth) {
        return ok(reviews.get(tenant(), id, HrAccess.held(auth), user(auth)));
    }

    @PostMapping("/reviews/{id}/self")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<ReviewDetail>> self(@PathVariable UUID id, @RequestBody SelfRequest req, Authentication auth) {
        return ok(reviews.submitSelf(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/reviews/{id}/manager")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<ReviewDetail>> manager(@PathVariable UUID id, @RequestBody ManagerRequest req, Authentication auth) {
        return ok(reviews.submitManager(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/reviews/{id}/reviewer")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ReviewDetail>> reviewer(@PathVariable UUID id, @RequestBody ReviewerRequest req, Authentication auth) {
        return ok(reviews.setReviewer(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/reviews/{id}/calibrate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ReviewDetail>> calibrate(@PathVariable UUID id, @RequestBody CalibrateRequest req, Authentication auth) {
        return ok(reviews.calibrate(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/reviews/{id}/share")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Release the result to the employee. After this nothing in the review can change")
    public ResponseEntity<ApiResponse<ReviewDetail>> share(@PathVariable UUID id, Authentication auth) {
        return ok(reviews.share(tenant(), id, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/reviews/{id}/exclude")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ReviewDetail>> exclude(@PathVariable UUID id, @RequestBody ReasonRequest req, Authentication auth) {
        return ok(reviews.exclude(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/reviews/{id}/acknowledge")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<ReviewDetail>> acknowledge(@PathVariable UUID id, @RequestBody(required = false) AcknowledgeRequest req, Authentication auth) {
        return ok(reviews.acknowledge(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    // ── 360 feedback ──────────────────────────────────────────────────────────────────────────────

    @PostMapping("/reviews/{id}/feedback")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<List<FeedbackView>>> askForFeedback(@PathVariable UUID id, @RequestBody FeedbackRequestBody req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Feedback requested", feedback.request(tenant(), id, req, user(auth), HrAccess.held(auth))));
    }

    @GetMapping("/feedback/mine")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Feedback I have been asked to give")
    public ResponseEntity<ApiResponse<List<FeedbackTask>>> myFeedback(Authentication auth) { return ok(feedback.mine(tenant(), user(auth))); }

    @PostMapping("/feedback/{id}/submit")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<FeedbackTask>> submitFeedback(@PathVariable UUID id, @RequestBody FeedbackSubmit req, Authentication auth) {
        return ok(feedback.submit(tenant(), id, req, user(auth)));
    }

    @PostMapping("/feedback/{id}/decline")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<FeedbackTask>> declineFeedback(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req, Authentication auth) {
        return ok(feedback.decline(tenant(), id, req == null ? new ReasonRequest(null) : req, user(auth)));
    }

    // ── Goals ─────────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/goals")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<List<GoalView>>> goals(@RequestParam(defaultValue = "mine") String scope, @RequestParam(required = false) UUID employeeId, Authentication auth) {
        return ok(goals.list(tenant(), scope, employeeId, HrAccess.held(auth), user(auth)));
    }

    @GetMapping("/goals/{id}")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<GoalView>> goal(@PathVariable UUID id, Authentication auth) { return ok(goals.get(tenant(), id, HrAccess.held(auth), user(auth))); }

    @PostMapping("/goals")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<GoalView>> createGoal(@RequestBody GoalRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Goal saved", goals.create(tenant(), req, user(auth), HrAccess.held(auth))));
    }

    @PutMapping("/goals/{id}")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<GoalView>> editGoal(@PathVariable UUID id, @RequestBody GoalEditRequest req, Authentication auth) {
        return ok(goals.edit(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/goals/{id}/approve")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<GoalView>> approveGoal(@PathVariable UUID id, Authentication auth) { return ok(goals.approve(tenant(), id, user(auth), HrAccess.held(auth))); }

    @PostMapping("/goals/{id}/progress")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<GoalView>> goalProgress(@PathVariable UUID id, @RequestBody ProgressRequest req, Authentication auth) {
        return ok(goals.progress(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/goals/{id}/close")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<GoalView>> closeGoal(@PathVariable UUID id, @RequestBody GoalCloseRequest req, Authentication auth) {
        return ok(goals.close(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/goals/{id}/cancel")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<GoalView>> cancelGoal(@PathVariable UUID id, @RequestBody ReasonRequest req, Authentication auth) {
        return ok(goals.cancel(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    // ── Improvement plans ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/plans")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Plans about me (scope=mine), that I own (team), all (HR_PERF_READ), for an employee, or for a case I can see")
    public ResponseEntity<ApiResponse<List<PlanRow>>> plans(@RequestParam(defaultValue = "mine") String scope, @RequestParam(required = false) UUID employeeId,
                                                           @RequestParam(required = false) UUID caseId, Authentication auth) {
        return ok(plans.list(tenant(), scope, employeeId, caseId, HrAccess.held(auth), user(auth)));
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<PlanDetail>> plan(@PathVariable UUID id, Authentication auth) { return ok(plans.get(tenant(), id, HrAccess.held(auth), user(auth))); }

    @PostMapping("/plans")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlanDetail>> createPlan(@RequestBody PlanRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Plan drafted", plans.create(tenant(), req, user(auth), HrAccess.held(auth))));
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlanDetail>> updatePlan(@PathVariable UUID id, @RequestBody PlanRequest req, Authentication auth) {
        return ok(plans.update(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/plans/{id}/start")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Record that the employee was told. The plan is then theirs to read and its terms are fixed")
    public ResponseEntity<ApiResponse<PlanDetail>> startPlan(@PathVariable UUID id, @RequestBody StartPlanRequest req, Authentication auth) {
        return ok(plans.start(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/plans/{id}/checkins")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<PlanDetail>> checkin(@PathVariable UUID id, @RequestBody CheckinRequest req, Authentication auth) {
        return ok(plans.checkin(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/plans/{id}/extend")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlanDetail>> extend(@PathVariable UUID id, @RequestBody ExtendRequest req, Authentication auth) {
        return ok(plans.extend(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/plans/{id}/close")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlanDetail>> closePlan(@PathVariable UUID id, @RequestBody ClosePlanRequest req, Authentication auth) {
        return ok(plans.close(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/plans/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlanDetail>> cancelPlan(@PathVariable UUID id, @RequestBody ReasonRequest req, Authentication auth) {
        return ok(plans.cancel(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/plans/{id}/acknowledge")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<PlanDetail>> acknowledgePlan(@PathVariable UUID id, @RequestBody(required = false) AcknowledgeRequest req, Authentication auth) {
        return ok(plans.acknowledge(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    @PostMapping("/plans/{id}/case")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PlanDetail>> linkCase(@PathVariable UUID id, @RequestBody CaseLinkRequest req, Authentication auth) {
        return ok(plans.linkCase(tenant(), id, req, user(auth), HrAccess.held(auth)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────────

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok(ApiResponse.success(body)); }

    private static za.co.handyflow.platform.shared.TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    private static UUID user(Authentication auth) {
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}
