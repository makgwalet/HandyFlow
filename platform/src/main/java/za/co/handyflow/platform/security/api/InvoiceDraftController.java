package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.InvoiceDraftOverviewService;
import za.co.handyflow.platform.security.application.internal.InvoiceDraftService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Invoice drafts")
public class InvoiceDraftController {

    public record CreateRequest(@NotBlank String month, String basis, String note) {}
    public record ApproveRequest(Boolean acknowledgeWarnings) {}
    public record VoidRequest(@NotBlank String reason) {}

    /** A draft with its frozen snapshot, plus the names of the people who saved, approved or voided it (by user id). */
    public record NamedDetail(InvoiceDraftService.Row draft, com.fasterxml.jackson.databind.JsonNode snapshot, java.util.Map<UUID, String> people) {}

    private final InvoiceDraftService service;
    private final InvoiceDraftOverviewService overview;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    /** Optional too: without it the draft is shown with ids only, never fails. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.PersonNameService personNames;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** The sites the user may see: null means the whole company. */
    private java.util.Set<UUID> allowed() {
        return za.co.handyflow.platform.security.application.internal.SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    @GetMapping("/api/v1/security/invoice-drafts")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Invoice drafts of all sites you may see, newest month first; optional month (2026-10) and status",
            description = "Without the snapshot (open one draft for that). At most 500; truncated says when more exist.")
    public ResponseEntity<ApiResponse<InvoiceDraftOverviewService.Overview>> listAll(@RequestParam(required = false) String month,
                                                                                    @RequestParam(required = false) String status) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", overview.list(TenantContext.getTenantIdAsObject(), month, status, allowed())));
    }

    @GetMapping("/api/v1/security/sites/{siteId}/invoice-drafts")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "The invoice drafts of a site, newest month first")
    public ResponseEntity<ApiResponse<List<InvoiceDraftService.Row>>> list(@PathVariable UUID siteId) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", service.list(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @GetMapping("/api/v1/security/sites/{siteId}/invoice-drafts/{id}")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "One invoice draft with the frozen billing preview it was made from")
    public ResponseEntity<ApiResponse<NamedDetail>> get(@PathVariable UUID siteId, @PathVariable UUID id) {
        featureGuard.requireModule("security");
        site(siteId);
        InvoiceDraftService.Detail d = service.get(TenantContext.getTenantIdAsObject(), siteId, id);
        return ResponseEntity.ok(ApiResponse.success("OK", new NamedDetail(d.draft(), d.snapshot(), people(d.draft()))));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/invoice-drafts")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Save the billing preview for a site and month as an invoice draft (not an invoice)",
            description = "month is required (2026-10). basis is SCHEDULED (default) or ACTUAL. One live draft per site and month; void it to make another. Audited.")
    public ResponseEntity<ApiResponse<InvoiceDraftService.Row>> create(@PathVariable UUID siteId, @Valid @RequestBody CreateRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("OK", service.create(TenantContext.getTenantIdAsObject(),
                TenantContext.getCurrentUserId(), siteId, r.month(), r.basis(), r.note())));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/invoice-drafts/{id}/approve")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Approve a draft. Refused for a month still in progress or a draft that bills nothing; hours with no rate or mixed VAT need acknowledgeWarnings.",
            description = "Whoever saved the draft does not approve it, unless nobody else could (recorded). Audited.")
    public ResponseEntity<ApiResponse<InvoiceDraftService.Row>> approve(@PathVariable UUID siteId, @PathVariable UUID id, @RequestBody(required = false) ApproveRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        boolean ack = r != null && Boolean.TRUE.equals(r.acknowledgeWarnings());
        return ResponseEntity.ok(ApiResponse.success("OK", service.approve(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId, id, ack)));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/invoice-drafts/{id}/void")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Void a draft or an approved draft, with a reason. Kept for the record; a new draft can then be made for the month.")
    public ResponseEntity<ApiResponse<InvoiceDraftService.Row>> voidDraft(@PathVariable UUID siteId, @PathVariable UUID id, @Valid @RequestBody VoidRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", service.voidDraft(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId, id, r.reason())));
    }

    /** Who saved, approved and voided it. A look-up that fails leaves the names out; it never stops the draft opening. */
    private java.util.Map<UUID, String> people(InvoiceDraftService.Row r) {
        if (personNames == null) return java.util.Map.of();
        try {
            return personNames.names(TenantContext.getTenantIdAsObject(), java.util.Arrays.asList(r.createdBy(), r.approvedBy(), r.voidedBy()));
        } catch (RuntimeException e) {
            return java.util.Map.of();
        }
    }
}
