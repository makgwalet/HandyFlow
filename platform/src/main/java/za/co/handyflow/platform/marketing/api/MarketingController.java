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
import za.co.handyflow.platform.marketing.application.internal.MarketingService;
import za.co.handyflow.platform.marketing.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing")
@RequiredArgsConstructor
@Tag(name = "Marketing", description = "Email campaigns, POPIA contacts and analytics")
public class MarketingController {

    private final MarketingService marketingService;

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
    @Operation(summary = "List all marketing contacts with opt-in status")
    public ResponseEntity<ApiResponse<Page<ContactPreferenceResponse>>> getContacts(
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                marketingService.getContacts(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/contacts/import")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Import contacts — POPIA: only import contacts that have given consent")
    public ResponseEntity<ApiResponse<Integer>> importContacts(
            @Valid @RequestBody ImportContactsRequest req) {
        int count = marketingService.importContacts(
                TenantContext.getTenantIdAsObject(), req);
        return ResponseEntity.ok(ApiResponse.success(
                count + " contacts imported", count));
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
    @Operation(summary = "Manually opt-in a contact")
    public ResponseEntity<ApiResponse<ContactPreferenceResponse>> optIn(
            @RequestParam String email,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "MANUAL") String source) {
        return ResponseEntity.ok(ApiResponse.success("Contact opted in",
                marketingService.optIn(TenantContext.getTenantIdAsObject(), email, name, source)));
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

    @PostMapping("/campaigns")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Create a campaign — status starts as DRAFT")
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(
            @Valid @RequestBody CreateCampaignRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Campaign created",
                marketingService.createCampaign(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PostMapping("/campaigns/{id}/launch")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Launch campaign — builds audience snapshot and queues emails for async send")
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
    // SecurityConfig must have /api/v1/marketing/unsubscribe/** in permitAll()

    @GetMapping("/unsubscribe/{token}")
    @Operation(summary = "PUBLIC — One-click unsubscribe via token from email footer")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@PathVariable String token) {
        marketingService.handleUnsubscribe(token);
        return ResponseEntity.ok(ApiResponse.success(
                "You have been unsubscribed. You will no longer receive marketing emails.", null));
    }

    // ── PUBLIC open/click tracking (no auth — fired directly by an email client) ──
    // SecurityConfig must have /api/v1/marketing/track/** in permitAll() too —
    // same requirement as unsubscribe above, easy to miss adding twice.

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
    @Operation(summary = "PUBLIC — records a campaign link click, then redirects to the real destination")
    public ResponseEntity<Void> trackClick(@PathVariable UUID campaignContactId,
                                           @RequestParam String url) {
        String target = marketingService.trackClick(campaignContactId, url);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}
