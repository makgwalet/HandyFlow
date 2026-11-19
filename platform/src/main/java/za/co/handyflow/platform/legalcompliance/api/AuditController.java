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
import za.co.handyflow.platform.legalcompliance.application.internal.AuditService;
import za.co.handyflow.platform.legalcompliance.domain.model.AuditStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.RemediationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Internal and external audit engagements, and the findings raised inside them. */
@RestController
@RequestMapping("/api/v1/legalcompliance/audits")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Audits", description = "Plan, run and complete audits; raise audit findings")
public class AuditController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_AUDIT_MANAGE','LEGALCOMPLIANCE_FINDING_MANAGE','LEGALCOMPLIANCE_FINDING_VERIFY','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_AUDIT = "hasAnyAuthority('LEGALCOMPLIANCE_AUDIT_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final AuditService audits;
    private final AuditFindingService findings;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<AuditResponse>>> list(@RequestParam(required = false) AuditStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(audits.list(tenant(), status)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<AuditSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(audits.summary(tenant())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<AuditResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(audits.get(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_AUDIT)
    public ResponseEntity<ApiResponse<AuditResponse>> plan(@Valid @RequestBody AuditRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Audit planned", audits.plan(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_AUDIT)
    public ResponseEntity<ApiResponse<AuditResponse>> update(@PathVariable UUID id, @Valid @RequestBody AuditRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Audit updated", audits.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(CAN_AUDIT)
    @Operation(summary = "Start fieldwork. Refused if an internal lead auditor owns a control in scope.")
    public ResponseEntity<ApiResponse<AuditResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Fieldwork started", audits.startFieldwork(tenant(), id, uid(), uname())));
    }

    @PostMapping("/{id}/report")
    @PreAuthorize(CAN_AUDIT)
    public ResponseEntity<ApiResponse<AuditResponse>> report(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Moved to reporting", audits.moveToReporting(tenant(), id, uid(), uname())));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CAN_AUDIT)
    public ResponseEntity<ApiResponse<AuditResponse>> complete(@PathVariable UUID id, @Valid @RequestBody CompleteAuditRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Audit completed", audits.complete(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_AUDIT)
    public ResponseEntity<ApiResponse<AuditResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Audit cancelled", audits.cancel(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/findings")
    @PreAuthorize(CAN_AUDIT)
    public ResponseEntity<ApiResponse<AuditFindingResponse>> raise(@PathVariable UUID id, @Valid @RequestBody AuditFindingRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Finding raised", findings.raise(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
