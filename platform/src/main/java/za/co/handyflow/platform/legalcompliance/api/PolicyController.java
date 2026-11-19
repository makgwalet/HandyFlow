package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.PolicyService;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.GovernanceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Policies, procedures and standards, their versions, approval and publication. */
@RestController
@RequestMapping("/api/v1/legalcompliance/policies")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Policies", description = "Draft, approve, publish and retire policies")
public class PolicyController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_POLICY_MANAGE','LEGALCOMPLIANCE_POLICY_APPROVE','LEGALCOMPLIANCE_ATTESTATION_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_POLICY_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_APPROVE = "hasAnyAuthority('LEGALCOMPLIANCE_POLICY_APPROVE','LEGALCOMPLIANCE_ADMIN')";

    private final PolicyService policies;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> list(@RequestParam(defaultValue = "false") boolean includeRetired) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(policies.list(tenant(), includeRetired)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<PolicySummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(policies.summary(tenant())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(policies.get(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> create(@Valid @RequestBody PolicyRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Policy created with a draft version 1", policies.create(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PolicyResponse>> update(@PathVariable UUID id, @Valid @RequestBody PolicyRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Policy updated", policies.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> newVersion(@PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Draft version created", policies.newVersion(tenant(), id, req, uid(), uname())));
    }

    @PutMapping("/versions/{versionId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> edit(@PathVariable UUID versionId, @Valid @RequestBody VersionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Draft saved", policies.editVersion(tenant(), versionId, req, uid(), uname())));
    }

    @PostMapping("/versions/{versionId}/submit")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> submit(@PathVariable UUID versionId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Submitted for approval", policies.submit(tenant(), versionId, uid(), uname())));
    }

    @PostMapping("/versions/{versionId}/approve")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Approve a version in review. Not by the person who wrote (last edited) it.")
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> approve(@PathVariable UUID versionId, @RequestBody(required = false) NoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Version approved", policies.approve(tenant(), versionId, req, uid(), uname())));
    }

    @PostMapping("/versions/{versionId}/return")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> returnForRework(@PathVariable UUID versionId, @Valid @RequestBody FindingNoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Returned to the author", policies.returnForRework(tenant(), versionId, req, uid(), uname())));
    }

    @PostMapping("/versions/{versionId}/publish")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Publish an approved version; supersedes the previous published version and closes attestation campaigns open on it.")
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> publish(@PathVariable UUID versionId, @RequestBody(required = false) PublishRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Version published", policies.publish(tenant(), versionId, req, uid(), uname())));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<PolicyResponse>> retire(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Policy retired", policies.retire(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
