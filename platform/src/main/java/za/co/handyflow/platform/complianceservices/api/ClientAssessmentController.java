package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ClientAssessmentService;
import za.co.handyflow.platform.complianceservices.dto.AssessmentDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Assessments", description = "Judging a client's obligations on one date, with a score")
public class ClientAssessmentController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";

    private final ClientAssessmentService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/assessment-weights")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<WeightsView>> weights() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.weights(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/assessment-weights")
    @PreAuthorize("hasAuthority('COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<WeightsView>> setWeights(@RequestBody WeightsRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Weights saved. They apply to assessments started from now on",
                service.setWeights(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{clientId}/assessments")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AssessmentSummary>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/clients/{clientId}/assessments")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssessmentView>> start(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Assessment started",
                service.start(TenantContext.getTenantIdAsObject(), clientId, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @GetMapping("/clients/{clientId}/assessments/{assessmentId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<AssessmentView>> get(@PathVariable UUID clientId, @PathVariable UUID assessmentId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), clientId, assessmentId)));
    }

    @PostMapping("/clients/{clientId}/assessments/{assessmentId}/recheck")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssessmentView>> recheck(@PathVariable UUID clientId, @PathVariable UUID assessmentId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Documents checked again",
                service.recheck(TenantContext.getTenantIdAsObject(), clientId, assessmentId, LocalDate.now())));
    }

    @PutMapping("/clients/{clientId}/assessments/{assessmentId}/items/{obligationId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssessmentView>> judge(@PathVariable UUID clientId, @PathVariable UUID assessmentId, @PathVariable UUID obligationId,
                                                              @Valid @RequestBody JudgeRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Judgement recorded",
                service.judge(TenantContext.getTenantIdAsObject(), clientId, assessmentId, obligationId, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/clients/{clientId}/assessments/{assessmentId}/complete")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssessmentView>> complete(@PathVariable UUID clientId, @PathVariable UUID assessmentId,
                                                                 @Valid @RequestBody(required = false) CompleteRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Assessment completed",
                service.complete(TenantContext.getTenantIdAsObject(), clientId, assessmentId, request, TenantContext.getCurrentUserId())));
    }
}
