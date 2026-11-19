package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.CapaService;
import za.co.handyflow.platform.legalcompliance.domain.model.CapaStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.RemediationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Corrective and preventive actions against findings. Evidence uses the shared evidence API (sourceModule=legalcompliance, relatedEntityType=CAPA). */
@RestController
@RequestMapping("/api/v1/legalcompliance/capas")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - CAPA", description = "Propose, approve, carry out and verify corrective and preventive actions")
public class CapaController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_AUDIT_MANAGE','LEGALCOMPLIANCE_FINDING_MANAGE','LEGALCOMPLIANCE_FINDING_VERIFY','LEGALCOMPLIANCE_CAPA_APPROVE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_FINDING_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_APPROVE = "hasAnyAuthority('LEGALCOMPLIANCE_CAPA_APPROVE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_VERIFY = "hasAnyAuthority('LEGALCOMPLIANCE_FINDING_VERIFY','LEGALCOMPLIANCE_ADMIN')";

    private final CapaService capas;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<CapaResponse>>> list(@RequestParam(required = false) CapaStatus status, @RequestParam(defaultValue = "false") boolean includeFinished,
                                                                @RequestParam(required = false) UUID findingId, @RequestParam(required = false) UUID auditFindingId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(capas.list(tenant(), status, includeFinished, findingId, auditFindingId)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<CapaSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(capas.summary(tenant())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<CapaDetailResponse>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(capas.detail(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CapaResponse>> propose(@Valid @RequestBody CapaRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Action proposed", capas.propose(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CapaResponse>> revise(@PathVariable UUID id, @Valid @RequestBody CapaReviseRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Action revised", capas.revise(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Approve a proposed action. Not by its proposer or owner.")
    public ResponseEntity<ApiResponse<CapaResponse>> approve(@PathVariable UUID id, @RequestBody(required = false) NoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Action approved", capas.approve(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<CapaResponse>> returnForRework(@PathVariable UUID id, @Valid @RequestBody FindingNoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Returned to the proposer", capas.returnForRework(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CapaResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Work started", capas.start(tenant(), id, uid(), uname())));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Mark the action done. Needs a note and at least one evidence file.")
    public ResponseEntity<ApiResponse<CapaResponse>> complete(@PathVariable UUID id, @Valid @RequestBody CompleteCapaRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Completed — awaiting independent verification", capas.complete(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize(CAN_VERIFY)
    @Operation(summary = "Verify that a completed action worked (effective=true) or send it back (effective=false). Not by its owner or the person who completed it.")
    public ResponseEntity<ApiResponse<CapaResponse>> verify(@PathVariable UUID id, @Valid @RequestBody VerifyCapaRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(req.effective() ? "Action verified" : "Sent back to the owner", capas.verify(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CapaResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Action cancelled", capas.cancel(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
