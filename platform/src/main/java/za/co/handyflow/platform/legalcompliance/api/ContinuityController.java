package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ContinuityService;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.ContinuityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Business continuity: processes, plans and exercises */
@RestController
@RequestMapping("/api/v1/legalcompliance/continuity")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Business continuity", description = "Critical processes, continuity plans and exercises")
public class ContinuityController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_BCM_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_BCM_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final ContinuityService continuity;
    private final FeatureGuard featureGuard;

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ContinuitySummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(continuity.summary(tenant())));
    }

    @GetMapping("/processes")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<ProcessResponse>>> processes(@RequestParam(defaultValue = "false") boolean includeRetired) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(continuity.listProcesses(tenant(), includeRetired)));
    }

    @GetMapping("/processes/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ProcessDetail>> process(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(continuity.getProcess(tenant(), id)));
    }

    @PostMapping("/processes")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> create(@Valid @RequestBody ProcessRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Process recorded", continuity.createProcess(tenant(), req, uid(), uname())));
    }

    @PutMapping("/processes/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> update(@PathVariable UUID id, @Valid @RequestBody ProcessRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Process updated", continuity.updateProcess(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/processes/{id}/bia-review")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> biaReview(@PathVariable UUID id, @Valid @RequestBody BiaReviewRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Impact analysis review recorded", continuity.biaReview(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/processes/{id}/retire")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> retire(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Process retired", continuity.retireProcess(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/processes/{id}/plans")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> draftPlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Plan drafted", continuity.createPlan(tenant(), id, req, uid(), uname())));
    }

    @PutMapping("/plans/{planId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> updatePlan(@PathVariable UUID planId, @Valid @RequestBody PlanRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Draft updated", continuity.updatePlan(tenant(), planId, req, uid(), uname())));
    }

    @PostMapping("/plans/{planId}/approve")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> approve(@PathVariable UUID planId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Plan approved", continuity.approvePlan(tenant(), planId, uid(), uname())));
    }

    @PostMapping("/plans/{planId}/confirm-review")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> confirmReview(@PathVariable UUID planId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Review recorded", continuity.confirmPlanReview(tenant(), planId, uid(), uname())));
    }

    @PostMapping("/plans/{planId}/retire")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> retirePlan(@PathVariable UUID planId, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Plan retired", continuity.retirePlan(tenant(), planId, req, uid(), uname())));
    }

    @PostMapping("/plans/{planId}/tests")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ProcessDetail>> test(@PathVariable UUID planId, @Valid @RequestBody TestRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Exercise recorded", continuity.recordTest(tenant(), planId, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
