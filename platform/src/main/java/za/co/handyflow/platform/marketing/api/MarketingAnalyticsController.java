package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingAnalyticsService;
import za.co.handyflow.platform.marketing.dto.AnalyticsDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing/analytics")
@RequiredArgsConstructor
@Tag(name = "Marketing Analytics", description = "Metric definitions, drilldowns, comparison and reconciliation")
public class MarketingAnalyticsController {

    private final MarketingAnalyticsService analytics;

    @GetMapping("/definitions")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<MetricDefinition>>> definitions() {
        return ResponseEntity.ok(ApiResponse.success(analytics.definitions()));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Totals and per-campaign metrics for campaigns in the last N days (1-365)")
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(ApiResponse.success(analytics.overview(TenantContext.getTenantIdAsObject(), days)));
    }

    @GetMapping("/compare")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Side-by-side metrics for 2-10 campaigns")
    public ResponseEntity<ApiResponse<List<CampaignMetrics>>> compare(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(ApiResponse.success(analytics.compare(TenantContext.getTenantIdAsObject(), ids)));
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<CampaignMetrics>> campaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(analytics.campaign(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/campaigns/{id}/timeline")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<TimelinePoint>>> timeline(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(analytics.timeline(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/campaigns/{id}/links")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<LinkStat>>> links(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(analytics.links(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/campaigns/{id}/recipients")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Per-recipient outcomes. outcome: ALL|SENT|DELIVERED|BOUNCED|COMPLAINED|OPENED|CLICKED|UNSUBSCRIBED|FAILED|SUPPRESSED|PENDING")
    public ResponseEntity<ApiResponse<Page<RecipientRow>>> recipients(
            @PathVariable UUID id, @RequestParam(defaultValue = "ALL") String outcome,
            @RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                analytics.recipients(TenantContext.getTenantIdAsObject(), id, outcome, search, pageable)));
    }

    @GetMapping("/campaigns/{id}/recipients/export")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "CSV of recipient outcomes (max 50,000 rows). Admin only: contains personal data.")
    public ResponseEntity<byte[]> export(@PathVariable UUID id, @RequestParam(defaultValue = "ALL") String outcome,
                                         @RequestParam(required = false) String search) {
        String csv = analytics.exportRecipientsCsv(TenantContext.getTenantIdAsObject(), id, outcome, search);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"campaign-recipients.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/campaigns/{id}/reconciliation")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<Reconciliation>> reconcile(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(analytics.reconcile(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/campaigns/{id}/reconciliation/repair")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Rewrite stored counters from the recipient rows. Refused while the campaign is sending.")
    public ResponseEntity<ApiResponse<Reconciliation>> repair(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Counters repaired",
                analytics.repair(TenantContext.getTenantIdAsObject(), id)));
    }
}