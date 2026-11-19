package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.AttestationService;
import za.co.handyflow.platform.legalcompliance.domain.model.CampaignStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.GovernanceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Attestation campaigns (managers) and a person's own attestations (any signed-in user). */
@RestController
@RequestMapping("/api/v1/legalcompliance/attestations")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Attestations", description = "Ask people to read and accept policies, and record their answers")
public class AttestationController {

    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_ATTESTATION_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final AttestationService attestations;
    private final FeatureGuard featureGuard;

    @GetMapping("/campaigns")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<List<CampaignResponse>>> campaigns(@RequestParam(required = false) CampaignStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(attestations.list(tenant(), status)));
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CampaignDetailResponse>> campaign(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(attestations.detail(tenant(), id)));
    }

    @PostMapping("/campaigns")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Launch a campaign on a published policy version to all active users (optionally one department).")
    public ResponseEntity<ApiResponse<CampaignResponse>> launch(@Valid @RequestBody CampaignRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Campaign launched", attestations.launch(tenant(), req, uid(), uname())));
    }

    @PostMapping("/campaigns/{id}/close")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CampaignResponse>> close(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Campaign closed", attestations.close(tenant(), id, req, uid(), uname())));
    }

    /** Deliberately open to any signed-in user: employees do not need module permissions to answer an attestation addressed to them. */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MyAttestation>>> mine() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(attestations.mine(tenant(), uid())));
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Answer your own attestation. Declining needs a comment. The answer can be changed while the campaign is open.")
    public ResponseEntity<ApiResponse<MyAttestation>> respond(@PathVariable UUID id, @RequestBody RespondRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Response recorded", attestations.respond(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
