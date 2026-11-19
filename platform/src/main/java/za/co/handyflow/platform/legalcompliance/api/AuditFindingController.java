package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.AuditFindingService;
import za.co.handyflow.platform.legalcompliance.domain.model.AuditFindingStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.RemediationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Audit findings: management response and independent closure. */
@RestController
@RequestMapping("/api/v1/legalcompliance/audit-findings")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Audit findings", description = "Respond to and close audit findings")
public class AuditFindingController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_AUDIT_MANAGE','LEGALCOMPLIANCE_FINDING_MANAGE','LEGALCOMPLIANCE_FINDING_VERIFY','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_FINDING_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_VERIFY = "hasAnyAuthority('LEGALCOMPLIANCE_FINDING_VERIFY','LEGALCOMPLIANCE_ADMIN')";

    private final AuditFindingService findings;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<AuditFindingResponse>>> list(@RequestParam(required = false) UUID auditId, @RequestParam(required = false) AuditFindingStatus status,
                                                                        @RequestParam(defaultValue = "false") boolean includeClosed) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(findings.list(tenant(), auditId, status, includeClosed)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<AuditFindingResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(findings.get(tenant(), id)));
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<AuditFindingResponse>> respond(@PathVariable UUID id, @Valid @RequestBody ManagementResponseRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Management response recorded", findings.respond(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(CAN_VERIFY)
    @Operation(summary = "Close an audit finding. Not by its owner or responder; needs every corrective/preventive action verified (observations excepted).")
    public ResponseEntity<ApiResponse<AuditFindingResponse>> close(@PathVariable UUID id, @Valid @RequestBody FindingNoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Finding closed", findings.close(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
