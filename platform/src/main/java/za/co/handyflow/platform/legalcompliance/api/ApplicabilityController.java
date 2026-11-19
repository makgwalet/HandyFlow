package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ApplicabilityService;
import za.co.handyflow.platform.legalcompliance.application.internal.ObligationGenerationService;
import za.co.handyflow.platform.legalcompliance.dto.ApplicabilityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Organisation profile, applicability assessments and obligation generation. */
@RestController
@RequestMapping("/api/v1/legalcompliance/applicability")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Applicability", description = "Which requirements apply to this organisation, and why")
public class ApplicabilityController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_ASSESS = "hasAnyAuthority('LEGALCOMPLIANCE_APPLICABILITY_ASSESS','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_APPROVE = "hasAnyAuthority('LEGALCOMPLIANCE_APPLICABILITY_APPROVE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final ApplicabilityService applicability;
    private final ObligationGenerationService generation;
    private final FeatureGuard featureGuard;

    @GetMapping("/profile")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ProfileResponse>> profile() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(applicability.getProfile(tenant())));
    }

    @PutMapping("/profile")
    @PreAuthorize(CAN_ASSESS)
    @Operation(summary = "Describe the organisation. Used only to produce suggestions — it never assigns anything.")
    public ResponseEntity<ApiResponse<ProfileResponse>> saveProfile(@Valid @RequestBody ProfileRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Profile saved", applicability.saveProfile(tenant(), uid(), uname(), req)));
    }

    @GetMapping("/worklist")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Requirements in CURRENT versions with a suggestion and the current decision (filter ALL | UNASSESSED | PENDING | APPLICABLE | NOT_APPLICABLE)")
    public ResponseEntity<ApiResponse<List<WorklistRow>>> worklist(@RequestParam(defaultValue = "ALL") String filter) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(applicability.worklist(tenant(), filter)));
    }

    @GetMapping("/requirements/{requirementId}/history")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<AssessmentResponse>>> history(@PathVariable UUID requirementId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(applicability.history(tenant(), requirementId)));
    }

    @PostMapping("/assessments")
    @PreAuthorize(CAN_ASSESS)
    @Operation(summary = "Record a justified decision. APPLICABLE is approved at once; NOT_APPLICABLE waits for approval.")
    public ResponseEntity<ApiResponse<AssessmentResponse>> assess(@Valid @RequestBody AssessRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Assessment recorded",
                applicability.assess(tenant(), uid(), uname(), req)));
    }

    @PostMapping("/assessments/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<AssessmentResponse>> approve(@PathVariable UUID id, @RequestBody(required = false) ApproveRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment approved", applicability.approve(tenant(), id, uid(), uname(),
                req == null ? new ApproveRequest(null, false) : req)));
    }

    @PostMapping("/assessments/{id}/reject")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<AssessmentResponse>> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment rejected", applicability.reject(tenant(), id, uid(), uname(), req)));
    }

    @PostMapping("/requirements/{requirementId}/generate-obligation")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Create an assigned obligation from an APPLICABLE requirement (idempotent)")
    public ResponseEntity<ApiResponse<GenerateObligationResponse>> generate(@PathVariable UUID requirementId,
                                                                           @Valid @RequestBody GenerateObligationRequest req) {
        featureGuard.requireModule("legalcompliance");
        GenerateObligationResponse res = generation.generate(tenant(), requirementId, uid(), uname(), req);
        return ResponseEntity.status(res.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(res.created() ? "Obligation created" : "Obligation already exists", res));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
