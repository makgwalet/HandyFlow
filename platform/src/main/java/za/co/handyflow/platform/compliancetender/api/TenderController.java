package za.co.handyflow.platform.compliancetender.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.businessreadiness.ReadinessAssessment;
import za.co.handyflow.platform.compliancetender.application.internal.TenderReadinessService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPersonnelService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPricingService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderSnapshotService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPdfService;
import za.co.handyflow.platform.compliancetender.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/tenders")
@RequiredArgsConstructor
@Tag(name = "Compliance - Tenders", description = "Tender workspace: opportunities, requirement matrix, lifecycle, outcome")
public class TenderController {

    private final TenderService tenderService;
    private final TenderPersonnelService personnelService;
    private final TenderSnapshotService snapshotService;
    private final TenderPdfService pdfService;
    private final TenderReadinessService readinessService;
    private final TenderPricingService pricingService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<TenderResponse>>> getTenders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getTenders(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<TenderResponse>> getTender(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getTender(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Create a new tender — assigns this tenant's own tender number")
    public ResponseEntity<ApiResponse<TenderResponse>> create(@Valid @RequestBody CreateTenderRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tender created",
                tenderService.create(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Correct a tender's details (not its number or status). Refused once the tender has a final outcome")
    public ResponseEntity<ApiResponse<TenderResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateTenderRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Tender updated",
                tenderService.update(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Move a tender to the next lifecycle status (DRAFT -> ... -> SUBMITTED -> ...)")
    public ResponseEntity<ApiResponse<TenderResponse>> transition(
            @PathVariable UUID id, @Valid @RequestBody TransitionTenderRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Tender status updated",
                tenderService.transition(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/outcome")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Record AWARDED or UNSUCCESSFUL, with reason and (if awarded) the awarded value")
    public ResponseEntity<ApiResponse<TenderResponse>> recordOutcome(
            @PathVariable UUID id, @Valid @RequestBody RecordTenderOutcomeRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Tender outcome recorded",
                tenderService.recordOutcome(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    // ── Requirement matrix ──────────────────────────────────────────────────

    @GetMapping("/{id}/requirements")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<TenderRequirementResponse>>> getRequirements(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                tenderService.getRequirements(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/readiness")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Judge this tender's requirements against the registrations and documents the business holds",
            description = "Read-only. Compares each requirement's evidence rule with the business's own records as of the closing date (or today if none) and reports "
                    + "met, missing, expired, pending or not evaluated, plus where the evidence disagrees with the status you set. It never changes a requirement's status.")
    public ResponseEntity<ApiResponse<ReadinessAssessment>> getReadiness(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(readinessService.assess(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/requirements")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<TenderRequirementResponse>> addRequirement(
            @PathVariable UUID id, @Valid @RequestBody CreateTenderRequirementRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requirement added",
                tenderService.addRequirement(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/requirements/{requirementId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Reword a custom requirement line while the tender is being prepared")
    public ResponseEntity<ApiResponse<TenderRequirementResponse>> renameRequirement(
            @PathVariable UUID requirementId, @Valid @RequestBody UpdateTenderRequirementRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Requirement updated",
                tenderService.renameRequirement(TenantContext.getTenantIdAsObject(), requirementId, request, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/requirements/{requirementId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Remove a requirement line while the tender is being prepared")
    public ResponseEntity<ApiResponse<Void>> removeRequirement(@PathVariable UUID requirementId) {
        featureGuard.requireModule("compliancetender");
        tenderService.removeRequirement(TenantContext.getTenantIdAsObject(), requirementId);
        return ResponseEntity.ok(ApiResponse.success("Requirement removed", null));
    }

    @PutMapping("/requirements/{requirementId}/status")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<TenderRequirementResponse>> updateRequirementStatus(
            @PathVariable UUID requirementId, @Valid @RequestBody UpdateTenderRequirementStatusRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Requirement status updated",
                tenderService.updateRequirementStatus(TenantContext.getTenantIdAsObject(), requirementId, request,
                        TenantContext.getCurrentUserId())));
    }

    // ── Personnel — referenced from HR, not copied ──────────────────────────

    @GetMapping("/{id}/personnel")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Key personnel for this tender — names/details are looked up live from HR, never duplicated")
    public ResponseEntity<ApiResponse<List<TenderPersonnelResponse>>> getPersonnel(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                personnelService.getPersonnel(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/personnel")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Reference an existing HR employee as key personnel for this tender")
    public ResponseEntity<ApiResponse<TenderPersonnelResponse>> addPersonnel(
            @PathVariable UUID id, @Valid @RequestBody AddTenderPersonnelRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Personnel added",
                personnelService.addPersonnel(TenantContext.getTenantIdAsObject(), id, request,
                        TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/personnel/{personnelId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removePersonnel(@PathVariable UUID personnelId) {
        featureGuard.requireModule("compliancetender");
        personnelService.removePersonnel(TenantContext.getTenantIdAsObject(), personnelId);
        return ResponseEntity.ok(ApiResponse.success("Personnel removed", null));
    }


    // ---- Pricing (ADR-004). Commercially sensitive, so even reading it needs MANAGE or ADMIN, not just READ. ----

    @GetMapping("/{id}/pricing")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "The tender's price schedule and computed breakdown")
    public ResponseEntity<ApiResponse<TenderPricingResponse>> getPricing(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(pricingService.getPricing(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/{id}/pricing/settings")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Set overhead, contingency, profit and VAT treatment (locked once submitted)")
    public ResponseEntity<ApiResponse<TenderPricingResponse>> saveSettings(
            @PathVariable UUID id, @Valid @RequestBody SaveTenderPricingSettingsRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Pricing settings saved",
                pricingService.saveSettings(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/pricing/lines")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Add a line to the price schedule (locked once submitted)")
    public ResponseEntity<ApiResponse<TenderPricingResponse>> addPricingLine(
            @PathVariable UUID id, @Valid @RequestBody SaveTenderPricingLineRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Line added",
                pricingService.addLine(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/pricing/lines/{lineId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Change a price schedule line (locked once submitted)")
    public ResponseEntity<ApiResponse<TenderPricingResponse>> updatePricingLine(
            @PathVariable UUID lineId, @Valid @RequestBody SaveTenderPricingLineRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Line updated",
                pricingService.updateLine(TenantContext.getTenantIdAsObject(), lineId, request, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/pricing/lines/{lineId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Remove a price schedule line (locked once submitted)")
    public ResponseEntity<ApiResponse<TenderPricingResponse>> deletePricingLine(@PathVariable UUID lineId) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Line removed",
                pricingService.deleteLine(TenantContext.getTenantIdAsObject(), lineId)));
    }

    // ── Submission snapshots — frozen at the moment a tender is SUBMITTED ───
    @GetMapping("/{id}/snapshots")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Every frozen submission record for this tender, most recent first")
    public ResponseEntity<ApiResponse<List<TenderSnapshotResponse>>> getSnapshots(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                snapshotService.getSnapshots(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/snapshots/{snapshotId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Exactly what was submitted — a frozen record, never affected by later changes")
    public ResponseEntity<ApiResponse<TenderSnapshotResponse>> getSnapshot(@PathVariable UUID snapshotId) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(
                snapshotService.getSnapshot(TenantContext.getTenantIdAsObject(), snapshotId)));
    }

    // ── PDF export ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Tender Summary PDF — current live state (requirement matrix + key personnel); includePricing adds the price schedule (MANAGE or ADMIN only)")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean includePricing) {
        featureGuard.requireModule("compliancetender");
        // pricing is commercially sensitive: asking for it without the right is refused, never quietly left out, so nobody thinks they exported the whole thing
        if (includePricing && !mayViewPricing()) throw new AccessDeniedException("Pricing is only available to people who can manage tenders.");
        byte[] pdf = pdfService.generateTenderSummaryPdf(TenantContext.getTenantIdAsObject(), id, includePricing);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tender-summary-" + id + (includePricing ? "-with-pricing" : "") + ".pdf\"")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .contentLength(pdf.length)
                .body(pdf);
    }

    private static boolean mayViewPricing() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("COMPLIANCE_MANAGE") || a.getAuthority().equals("COMPLIANCE_ADMIN"));
    }
}
