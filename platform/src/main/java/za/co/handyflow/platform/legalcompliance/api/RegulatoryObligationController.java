package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.LegalCompliancePdfService;
import za.co.handyflow.platform.legalcompliance.application.internal.RegulatoryObligationService;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;
import za.co.handyflow.platform.legalcompliance.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Regulatory obligation tracker — CRUD plus the review/non-compliance
 * workflow. FeatureGuard.requireModule("legalcompliance") is called at the
 * top of every handler (not once via @ModelAttribute, matching
 * RecruitmentAgencyController/BookingAgencyController's own established
 * per-method style rather than CustomerController's @ModelAttribute
 * variant — both patterns exist in this codebase; per-method is the more
 * common of the two among newer modules).
 */
@RestController
@RequestMapping("/api/v1/legalcompliance/obligations")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Regulatory Obligations", description = "Regulatory obligation tracker")
public class RegulatoryObligationController {

    private final RegulatoryObligationService obligationService;
    private final LegalCompliancePdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "List regulatory obligations, optionally filtered by status")
    public ResponseEntity<ApiResponse<Page<RegulatoryObligationResponse>>> list(
            @RequestParam(required = false) ObligationStatus status,
            @PageableDefault(size = 20, sort = "reviewDate") Pageable pageable) {
        featureGuard.requireModule("legalcompliance");
        Page<RegulatoryObligationResponse> page = obligationService
                .list(TenantContext.getTenantIdAsObject(), status, pageable)
                .map(RegulatoryObligationResponse::of);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/due-within")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Obligations with a review date within the given number of days")
    public ResponseEntity<ApiResponse<List<RegulatoryObligationResponse>>> dueWithin(
            @RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("legalcompliance");
        List<RegulatoryObligationResponse> result = obligationService
                .findDueWithin(TenantContext.getTenantIdAsObject(), days).stream()
                .map(RegulatoryObligationResponse::of).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<RegulatoryObligationResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        RegulatoryObligation obligation = obligationService.get(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(RegulatoryObligationResponse.of(obligation)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Create a regulatory obligation")
    public ResponseEntity<ApiResponse<RegulatoryObligationResponse>> create(
            @Valid @RequestBody CreateRegulatoryObligationRequest req) {
        featureGuard.requireModule("legalcompliance");
        RegulatoryObligation obligation = obligationService.create(
                TenantContext.getTenantIdAsObject(), req.title(), req.category(), req.regulationReference(),
                req.description(), req.responsibleUserId(), req.responsibleUserName(), req.reviewDate(),
                req.recurrence(), req.linkedContractId(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Regulatory obligation created", RegulatoryObligationResponse.of(obligation)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<RegulatoryObligationResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateRegulatoryObligationRequest req) {
        featureGuard.requireModule("legalcompliance");
        RegulatoryObligation obligation = obligationService.update(
                TenantContext.getTenantIdAsObject(), id, req.title(), req.regulationReference(), req.description(),
                req.responsibleUserId(), req.responsibleUserName(), req.reviewDate(), req.recurrence());
        return ResponseEntity.ok(ApiResponse.success("Obligation updated", RegulatoryObligationResponse.of(obligation)));
    }

    @PostMapping("/{id}/mark-reviewed")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Record a review — rolls the review date forward by one recurrence interval")
    public ResponseEntity<ApiResponse<RegulatoryObligationResponse>> markReviewed(
            @PathVariable UUID id, @RequestBody(required = false) MarkReviewedRequest req) {
        featureGuard.requireModule("legalcompliance");
        String notes = req != null ? req.notes() : null;
        RegulatoryObligation obligation = obligationService.markReviewed(TenantContext.getTenantIdAsObject(), id,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), notes);
        return ResponseEntity.ok(ApiResponse.success("Obligation marked reviewed", RegulatoryObligationResponse.of(obligation)));
    }

    @PostMapping("/{id}/mark-non-compliant")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Record a real non-compliance finding — never set automatically")
    public ResponseEntity<ApiResponse<RegulatoryObligationResponse>> markNonCompliant(
            @PathVariable UUID id, @Valid @RequestBody MarkNonCompliantRequest req) {
        featureGuard.requireModule("legalcompliance");
        RegulatoryObligation obligation = obligationService.markNonCompliant(
                TenantContext.getTenantIdAsObject(), id, req.notes());
        return ResponseEntity.ok(ApiResponse.success("Obligation marked non-compliant", RegulatoryObligationResponse.of(obligation)));
    }

    @PostMapping("/{id}/link-contract")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Link this obligation to a contract (read-only reference via ContractingFacade)")
    public ResponseEntity<ApiResponse<RegulatoryObligationResponse>> linkContract(
            @PathVariable UUID id, @Valid @RequestBody LinkContractRequest req) {
        featureGuard.requireModule("legalcompliance");
        RegulatoryObligation obligation = obligationService.linkContract(
                TenantContext.getTenantIdAsObject(), id, req.contractId());
        return ResponseEntity.ok(ApiResponse.success("Contract linked", RegulatoryObligationResponse.of(obligation)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        obligationService.delete(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Obligation deleted", null));
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Export the full obligation register as a PDF")
    public ResponseEntity<byte[]> exportPdf() {
        featureGuard.requireModule("legalcompliance");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = pdfService.generateObligationRegister(null, obligationService.listAll(tenantId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"regulatory-obligation-register.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
