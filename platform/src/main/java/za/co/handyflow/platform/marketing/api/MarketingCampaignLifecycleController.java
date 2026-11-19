package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingContentService;
import za.co.handyflow.platform.marketing.application.internal.MarketingLifecycleService;
import za.co.handyflow.platform.marketing.application.internal.MarketingNotificationService;
import za.co.handyflow.platform.marketing.application.internal.MarketingService;
import za.co.handyflow.platform.marketing.dto.CampaignResponse;
import za.co.handyflow.platform.marketing.dto.StudioDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing")
@RequiredArgsConstructor
@Tag(name = "Marketing Lifecycle", description = "Edit, versions, approval, test sends")
public class MarketingCampaignLifecycleController {

    private final MarketingLifecycleService lifecycle;
    private final MarketingContentService   content;
    private final MarketingService          marketing;
    private final MarketingNotificationService notifications;

    private ResponseEntity<ApiResponse<CampaignResponse>> reload(String msg, UUID id) {
        return ResponseEntity.ok(ApiResponse.success(msg, marketing.getCampaign(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Approval settings")
    public ResponseEntity<ApiResponse<MarketingSettings>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(lifecycle.settings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Update approval settings (admin)")
    public ResponseEntity<ApiResponse<MarketingSettings>> updateSettings(@RequestBody MarketingSettings req) {
        return ResponseEntity.ok(ApiResponse.success("Settings saved",
                lifecycle.updateSettings(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/campaigns/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Edit a DRAFT/SCHEDULED campaign. Content changes create a new version and void any approval.")
    public ResponseEntity<ApiResponse<CampaignResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignRequest req) {
        lifecycle.update(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req);
        return reload("Campaign updated", id);
    }

    @PostMapping("/campaigns/{id}/submit")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Submit the current version for review (blocked while the content has errors)")
    public ResponseEntity<ApiResponse<CampaignResponse>> submit(@PathVariable UUID id) {
        lifecycle.submit(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id);
        return reload("Submitted for review", id);
    }

    @PostMapping("/campaigns/{id}/withdraw")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Withdraw a submission")
    public ResponseEntity<ApiResponse<CampaignResponse>> withdraw(@PathVariable UUID id) {
        lifecycle.withdraw(TenantContext.getTenantIdAsObject(), id);
        return reload("Submission withdrawn", id);
    }

    @PostMapping("/campaigns/{id}/approve")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Approve the version under review (four-eyes unless self-approval is allowed)")
    public ResponseEntity<ApiResponse<CampaignResponse>> approve(@PathVariable UUID id, @RequestBody(required = false) ReviewDecisionRequest req) {
        lifecycle.approve(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req == null ? null : req.note());
        return reload("Campaign approved", id);
    }

    @PostMapping("/campaigns/{id}/request-changes")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Send a submission back with a required note")
    public ResponseEntity<ApiResponse<CampaignResponse>> requestChanges(@PathVariable UUID id, @RequestBody ReviewDecisionRequest req) {
        lifecycle.requestChanges(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req.note());
        return reload("Changes requested", id);
    }

    @PostMapping("/campaigns/{id}/test-send")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Send a [TEST] copy to up to 5 addresses. No tracking, no consent side effects.")
    public ResponseEntity<ApiResponse<TestSendResult>> testSend(@PathVariable UUID id, @RequestBody TestSendRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Test processed",
                content.testSend(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req.emails())));
    }

    @GetMapping("/campaigns/{id}/versions")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Version history (without bodies)")
    public ResponseEntity<ApiResponse<List<CampaignVersionResponse>>> versions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(lifecycle.listVersions(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/campaigns/{id}/versions/{version}")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "One version, including its body")
    public ResponseEntity<ApiResponse<CampaignVersionResponse>> version(@PathVariable UUID id, @PathVariable int version) {
        return ResponseEntity.ok(ApiResponse.success(lifecycle.getVersion(TenantContext.getTenantIdAsObject(), id, version)));
    }

    @GetMapping("/notifications/approvers")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<String>>> approvers() {
        return ResponseEntity.ok(ApiResponse.success(notifications.approvers(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/notifications/approvers")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> setApprovers(@RequestBody List<String> emails) {
        return ResponseEntity.ok(ApiResponse.success("Saved",
                notifications.setApprovers(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), emails)));
    }
}