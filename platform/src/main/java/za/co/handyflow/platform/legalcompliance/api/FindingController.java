package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.*;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Findings raised by failed or partial control tests, their remediation and independent verification. */
@RestController
@RequestMapping("/api/v1/legalcompliance/findings")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Findings", description = "Remediate findings and verify them against a passing retest")
public class FindingController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_FINDING_MANAGE','LEGALCOMPLIANCE_FINDING_VERIFY','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_FINDING_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_VERIFY = "hasAnyAuthority('LEGALCOMPLIANCE_FINDING_VERIFY','LEGALCOMPLIANCE_ADMIN')";

    private final FindingService findings;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<FindingResponse>>> list(@RequestParam(required = false) FindingStatus status, @RequestParam(required = false) UUID controlId,
                                                                   @RequestParam(defaultValue = "false") boolean includeClosed) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(findings.list(tenant(), status, controlId, includeClosed)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<FindingSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(findings.summary(tenant())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<FindingResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(findings.get(tenant(), id)));
    }

    @PostMapping("/{id}/remediation")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FindingResponse>> remediation(@PathVariable UUID id, @Valid @RequestBody RemediationRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Remediation planned", findings.startRemediation(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FindingResponse>> resolve(@PathVariable UUID id, @Valid @RequestBody FindingNoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Marked resolved — awaiting independent verification", findings.resolve(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/reject-resolution")
    @PreAuthorize(CAN_VERIFY)
    public ResponseEntity<ApiResponse<FindingResponse>> reject(@PathVariable UUID id, @Valid @RequestBody FindingNoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Sent back to remediation", findings.rejectResolution(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(CAN_VERIFY)
    @Operation(summary = "Close a resolved finding. Needs a verifier who did not resolve it and a completed, passing retest of the same control.")
    public ResponseEntity<ApiResponse<FindingResponse>> close(@PathVariable UUID id, @Valid @RequestBody CloseFindingRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Finding closed", findings.close(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
