package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.Finding;
import za.co.handyflow.platform.debtcollection.application.internal.LegalHandoverService;
import za.co.handyflow.platform.debtcollection.application.internal.LegalHandoverService.ActionResult;
import za.co.handyflow.platform.debtcollection.domain.model.LegalHandover;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Controlled legal handover. Preparing and cancelling are MANAGE/ADMIN; approving, returning, transferring and
 * syncing legal status are supervisor actions (DEBTCOLLECTION_ADMIN), and approval additionally requires a
 * different person from the preparer. Steps that a BLOCKING finding stops return 200 with done=false and the
 * findings — fix them and call again.
 */
@RestController
@RequestMapping("/api/v1/debtcollection")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Legal Handover", description = "Reviewed transfer of cases to Legal Practice")
public class LegalHandoverController {

    private final LegalHandoverService service;
    private final FeatureGuard featureGuard;

    @PostMapping("/cases/{caseId}/legal-handovers")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Start a handover draft for a case (one open handover per case)")
    public ResponseEntity<ApiResponse<HandoverResponse>> create(@PathVariable UUID caseId,
                                                                @Valid @RequestBody HandoverDraftRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalHandover h = service.create(tenant(), caseId, req.agreementType(), req.creditorAuthorityNote(),
                req.evidenceIds(), req.notes(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Handover drafted", HandoverResponse.of(h)));
    }

    @GetMapping("/cases/{caseId}/legal-handovers")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<HandoverResponse>>> list(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                service.listForCase(tenant(), caseId).stream().map(HandoverResponse::of).toList()));
    }

    @GetMapping("/legal-handovers/{id}")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<HandoverResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(HandoverResponse.of(service.get(tenant(), id))));
    }

    @PutMapping("/legal-handovers/{id}")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Edit a DRAFT handover")
    public ResponseEntity<ApiResponse<HandoverResponse>> update(@PathVariable UUID id,
                                                                @Valid @RequestBody HandoverDraftRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalHandover h = service.updateDraft(tenant(), id, req.agreementType(), req.creditorAuthorityNote(),
                req.evidenceIds(), req.notes(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Draft updated", HandoverResponse.of(h)));
    }

    @PostMapping("/legal-handovers/{id}/prescription-review")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record that the prescription position has been reviewed (DRAFT only)")
    public ResponseEntity<ApiResponse<HandoverResponse>> prescriptionReview(@PathVariable UUID id,
                                                                            @Valid @RequestBody PrescriptionReviewRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalHandover h = service.recordPrescriptionReview(tenant(), id, req.note(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Prescription review recorded", HandoverResponse.of(h)));
    }

    @GetMapping("/legal-handovers/{id}/readiness")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Run the pre-handover checks now (BLOCKING and WARNING findings)")
    public ResponseEntity<ApiResponse<List<Finding>>> readiness(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(service.readiness(tenant(), id)));
    }

    @PostMapping("/legal-handovers/{id}/submit")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Run checks, freeze the transfer package (new version + hash) and submit for review")
    public ResponseEntity<ApiResponse<HandoverActionResponse>> submit(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return action(service.submit(tenant(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()),
                "Submitted for review");
    }

    @PostMapping("/legal-handovers/{id}/approve")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Approve (four-eyes: approver must differ from the preparer; checks are re-run)")
    public ResponseEntity<ApiResponse<HandoverActionResponse>> approve(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return action(service.approve(tenant(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()),
                "Handover approved");
    }

    @PostMapping("/legal-handovers/{id}/return")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Send an IN_REVIEW/APPROVED handover back to DRAFT with a reason; resubmitting builds a new package version")
    public ResponseEntity<ApiResponse<HandoverResponse>> returnToDraft(@PathVariable UUID id,
                                                                       @Valid @RequestBody HandoverReasonRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalHandover h = service.returnToDraft(tenant(), id, req.reason(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Returned to draft", HandoverResponse.of(h)));
    }

    @PostMapping("/legal-handovers/{id}/transfer")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Transfer an APPROVED handover to Legal Practice; creates the linked matter and moves the case to HANDED_TO_LEGAL")
    public ResponseEntity<ApiResponse<HandoverActionResponse>> transfer(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return action(service.transfer(tenant(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()),
                "Handover transferred to Legal Practice");
    }

    @PostMapping("/legal-handovers/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<HandoverResponse>> cancel(@PathVariable UUID id,
                                                                @Valid @RequestBody HandoverReasonRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalHandover h = service.cancel(tenant(), id, req.reason(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Handover cancelled", HandoverResponse.of(h)));
    }

    @PostMapping("/legal-handovers/{id}/legal-status")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record the latest status reported by Legal Practice (manual path; the legal module normally calls the service)")
    public ResponseEntity<ApiResponse<HandoverResponse>> legalStatus(@PathVariable UUID id,
                                                                     @Valid @RequestBody LegalStatusRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalHandover h = service.syncLegalStatus(tenant(), id, req.legalStatus(), req.detail(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Legal status recorded", HandoverResponse.of(h)));
    }

    @GetMapping(value = "/legal-handovers/{id}/package", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "The frozen transfer package JSON (contains personal data); X-Package-Sha256 carries its hash")
    public ResponseEntity<String> packageJson(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        LegalHandover h = service.get(tenant(), id);
        if (h.getPackageJson() == null) {
            throw new IllegalStateException("No package yet — submit the handover to build one");
        }
        return ResponseEntity.ok().header("X-Package-Sha256", h.getPackageHash()).body(h.getPackageJson());
    }

    @GetMapping("/legal-handovers/{id}/history")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<LegalEventResponse>>> history(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                service.history(tenant(), id).stream().map(LegalEventResponse::of).toList()));
    }

    private ResponseEntity<ApiResponse<HandoverActionResponse>> action(ActionResult r, String doneMessage) {
        return ResponseEntity.ok(ApiResponse.success(r.done() ? doneMessage : "Not ready — resolve the blocking findings",
                HandoverActionResponse.of(r)));
    }

    private TenantId tenant() {
        return TenantContext.getTenantIdAsObject();
    }
}
