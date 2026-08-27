// security/api/PublicApiController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.PublicApiService;
import za.co.handyflow.platform.security.domain.model.ApiKey;
import za.co.handyflow.platform.security.domain.model.WebhookDelivery;
import za.co.handyflow.platform.security.domain.repository.WebhookDeliveryRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Tag(name = "Security - Public API & Webhooks (Phase 4)")
@RestController
@RequestMapping("/api/v1/security/public-api")
@RequiredArgsConstructor
public class PublicApiController {

    private final PublicApiService          publicApiService;
    private final WebhookDeliveryRepository deliveryRepository;

    // ── API Keys ───────────────────────────────────────────────────────────────

    @GetMapping("/keys")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all active API keys for this tenant")
    public ResponseEntity<ApiResponse<List<ApiKey>>> listApiKeys() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(publicApiService.listApiKeys(tenantId)));
    }

    @PostMapping("/keys")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Create an API key",
            description = """
            The rawKey in the response is shown ONCE — copy it immediately.
            It cannot be retrieved afterward; only its SHA-256 hash is stored.

            scopePrefixesJson: JSON array of allowed path prefixes, e.g.
            ["/api/v1/security/reports", "/api/v1/security/sites"]
            NULL = full read access to all security endpoints.

            readOnly: true (default) prevents write operations even if the
            scope prefix would otherwise allow them.
            """)
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(publicApiService.createApiKey(tenantId, req, actorId)));
    }

    @DeleteMapping("/keys/{id}")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Revoke an API key — immediate, cannot be undone")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "Revoked by administrator") String reason) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID actorId = TenantContext.getCurrentUserId();
        publicApiService.revokeApiKey(tenantId, id, reason, actorId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Webhooks ───────────────────────────────────────────────────────────────

    @GetMapping("/webhooks")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all webhook subscriptions")
    public ResponseEntity<ApiResponse<List<WebhookSubscriptionResponse>>> listWebhooks() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(publicApiService.listWebhooks(tenantId)));
    }

    @PostMapping("/webhooks")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Subscribe to webhook events",
            description = """
            eventTypesJson: JSON array of event types to subscribe to.
            Supported: ALARM_EVENT | DISPATCH_CREATED | DISPATCH_RESOLVED |
            INCIDENT_CREATED | INCIDENT_RESOLVED | SHIFT_MISSED |
            PATROL_ROUND_MISSED | DURESS_TRIGGERED | GUARD_SCREENING_DUE |
            PSIRA_EXPIRY_WARNING

            HandyFlow will POST to your endpointUrl with header:
            X-HandyFlow-Signature: sha256=<HMAC-SHA256 of body using signing secret>
            The signing secret is set at creation time and not retrievable afterward.
            After 10 consecutive delivery failures, the subscription is suspended.
            """)
    public ResponseEntity<ApiResponse<WebhookSubscriptionResponse>> createWebhook(
            @Valid @RequestBody CreateWebhookRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(publicApiService.createWebhook(tenantId, req, actorId)));
    }

    @DeleteMapping("/webhooks/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Deactivate a webhook subscription")
    public ResponseEntity<ApiResponse<Void>> deactivateWebhook(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        publicApiService.deactivateWebhook(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/webhooks/{id}/reactivate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Reactivate a suspended webhook and reset failure count")
    public ResponseEntity<ApiResponse<Void>> reactivateWebhook(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        publicApiService.reactivateWebhook(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/webhooks/{id}/deliveries")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Delivery log for a webhook subscription — newest first")
    public ResponseEntity<ApiResponse<Page<WebhookDelivery>>> getDeliveries(
            @PathVariable UUID id, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                deliveryRepository.findBySubscription(id, pageable)));
    }
}