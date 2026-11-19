package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingAudienceService;
import za.co.handyflow.platform.marketing.application.internal.MarketingDeliveryEventService;
import za.co.handyflow.platform.marketing.application.internal.MarketingService;
import za.co.handyflow.platform.marketing.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing")
@RequiredArgsConstructor
@Tag(name = "Marketing", description = "Email campaigns, POPIA contacts and analytics")
public class MarketingController {

    private final MarketingService              marketingService;
    private final MarketingDeliveryEventService deliveryEventService;
    private final MarketingAudienceService      audienceService;

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Marketing dashboard — contacts, campaign stats")
    public ResponseEntity<ApiResponse<MarketingSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                marketingService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Contacts (POPIA) ──────────────────────────────────────────────────────

    @GetMapping("/contacts")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Search contacts (server-side). status: ALL | OPTED_IN | NOT_OPTED_IN | SUPPRESSED")
    public ResponseEntity<ApiResponse<Page<ContactPreferenceResponse>>> getContacts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String source,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                marketingService.getContacts(TenantContext.getTenantIdAsObject(), search, status, source, pageable)));
    }

    @GetMapping("/contacts/export")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Export contacts matching the filters as CSV (max 50,000 rows). Admin only.")
    public ResponseEntity<byte[]> exportContacts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String source) {
        String csv = audienceService.exportContactsCsv(
                TenantContext.getTenantIdAsObject().getValue(), search, status, source);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"marketing-contacts.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/contacts/{id}/consent-history")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Proof-of-permission history for a contact (opt-ins, opt-outs, suppressions)")
    public ResponseEntity<ApiResponse<List<ConsentEvidenceResponse>>> getConsentHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                marketingService.getConsentHistory(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/contacts/import")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Import contacts — POPIA: only import contacts that have given consent. Suppressed and invalid addresses are skipped.")
    public ResponseEntity<ApiResponse<Integer>> importContacts(
            @Valid @RequestBody ImportContactsRequest req) {
        ImportContactsResult result = marketingService.importContacts(
                TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req);
        String message = result.imported() + " contacts imported"
                + (result.skippedSuppressed() > 0 ? ", " + result.skippedSuppressed() + " skipped (suppressed)" : "")
                + (result.skippedInvalid() > 0 ? ", " + result.skippedInvalid() + " skipped (invalid email)" : "");
        return ResponseEntity.ok(ApiResponse.success(message, result.imported()));
    }

    @PostMapping("/contacts/sync-crm")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Sync CRM customers into marketing contacts list (without opt-in — must be collected separately)")
    public ResponseEntity<ApiResponse<Integer>> syncCrmContacts() {
        int count = marketingService.syncCrmContacts(TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok(ApiResponse.success(
                count + " CRM contacts synced", count));
    }

    @PostMapping("/contacts/opt-in")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Manually opt-in a contact. Rejected (409) if the address is suppressed. Supply evidence of consent.")
    public ResponseEntity<ApiResponse<ContactPreferenceResponse>> optIn(
            @RequestParam String email,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "MANUAL") String source,
            @RequestParam(required = false) String evidence) {
        return ResponseEntity.ok(ApiResponse.success("Contact opted in",
                marketingService.optIn(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), email, name, source, evidence)));
    }

    // ── Suppression list ──────────────────────────────────────────────────────

    @GetMapping("/suppressions")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "List suppressed addresses (active only by default)")
    public ResponseEntity<ApiResponse<Page<SuppressionResponse>>> getSuppressions(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                marketingService.getSuppressions(TenantContext.getTenantIdAsObject(), activeOnly, pageable)));
    }

    @PostMapping("/suppressions")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Add an address to the suppression list — also opts the contact out and stops queued sends")
    public ResponseEntity<ApiResponse<SuppressionResponse>> addSuppression(
            @Valid @RequestBody AddSuppressionRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Address suppressed",
                marketingService.addSuppression(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PostMapping("/suppressions/{id}/lift")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Lift a suppression with a documented reason. Does NOT opt the contact back in.")
    public ResponseEntity<ApiResponse<SuppressionResponse>> liftSuppression(
            @PathVariable UUID id,
            @Valid @RequestBody LiftSuppressionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Suppression lifted",
                marketingService.liftSuppression(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), id, req.reason())));
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "List email templates")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> getTemplates() {
        return ResponseEntity.ok(ApiResponse.success(
                marketingService.getTemplates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Create an email template — supports {{first_name}}, {{company_name}}, {{unsubscribe_url}} tokens")
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @Valid @RequestBody CreateTemplateRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Template created",
                marketingService.createTemplate(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Update an email template")
    public ResponseEntity<ApiResponse<TemplateResponse>> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTemplateRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Template updated",
                marketingService.updateTemplate(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Campaigns ─────────────────────────────────────────────────────────────

    @GetMapping("/campaigns")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "List campaigns")
    public ResponseEntity<ApiResponse<Page<CampaignResponse>>> getCampaigns(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                marketingService.getCampaigns(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Get campaign detail with analytics")
    public ResponseEntity<ApiResponse<CampaignResponse>> getCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                marketingService.getCampaign(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/campaigns/{id}/delivery-events")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Latest 200 provider delivery events (delivered, bounces, complaints) for a campaign")
    public ResponseEntity<ApiResponse<List<DeliveryEventResponse>>> getDeliveryEvents(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        marketingService.getCampaign(tenantId, id);   // 404 if the campaign isn't this tenant's
        return ResponseEntity.ok(ApiResponse.success(deliveryEventService.listForCampaign(tenantId, id)));
    }

    @PostMapping("/campaigns")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Create a campaign — DRAFT, or SCHEDULED when scheduledAt is supplied. Audience: ALL_OPTED_IN or SEGMENT (+ segmentId).")
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(
            @Valid @RequestBody CreateCampaignRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Campaign created",
                marketingService.createCampaign(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PostMapping("/campaigns/{id}/launch")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Launch (DRAFT/SCHEDULED) or resume (PAUSED) — resolves the audience, snapshots eligible recipients, excludes suppressed ones")
    public ResponseEntity<ApiResponse<CampaignResponse>> launchCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Campaign launched — emails queued",
                marketingService.launchCampaign(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/campaigns/{id}/pause")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Pause a sending campaign")
    public ResponseEntity<ApiResponse<CampaignResponse>> pauseCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Campaign paused",
                marketingService.pauseCampaign(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/campaigns/{id}/cancel")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Cancel a campaign")
    public ResponseEntity<ApiResponse<CampaignResponse>> cancelCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Campaign cancelled",
                marketingService.cancelCampaign(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── PUBLIC unsubscribe (no auth — token in URL) ───────────────────────────

    @GetMapping("/unsubscribe/{token}")
    @Operation(summary = "PUBLIC — One-click unsubscribe via token from email footer. 404 for an invalid token.")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@PathVariable String token) {
        marketingService.handleUnsubscribe(token);
        return ResponseEntity.ok(ApiResponse.success(
                "You have been unsubscribed. You will no longer receive marketing emails.", null));
    }

    @PostMapping("/unsubscribe/{token}")
    @Operation(summary = "PUBLIC — RFC 8058 List-Unsubscribe-Post one-click unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribeOneClick(@PathVariable String token) {
        marketingService.handleUnsubscribe(token);
        return ResponseEntity.ok(ApiResponse.success("Unsubscribed", null));
    }

    // ── PUBLIC open/click tracking (no auth — fired directly by an email client) ──

    @GetMapping(value = "/track/open/{campaignContactId}", produces = MediaType.IMAGE_GIF_VALUE)
    @Operation(summary = "PUBLIC — 1x1 tracking pixel, records a campaign email open")
    public ResponseEntity<byte[]> trackOpen(@PathVariable UUID campaignContactId) {
        byte[] pixel = marketingService.trackOpen(campaignContactId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .contentType(MediaType.IMAGE_GIF)
                .body(pixel);
    }

    @GetMapping("/track/click/{campaignContactId}")
    @Operation(summary = "PUBLIC — records a click, then redirects. Destination is validated against the campaign's own link hosts.")
    public ResponseEntity<Void> trackClick(@PathVariable UUID campaignContactId,
                                           @RequestParam String url) {
        String target = marketingService.trackClick(campaignContactId, url);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}