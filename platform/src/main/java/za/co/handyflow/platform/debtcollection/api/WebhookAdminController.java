package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookService;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookService.Created;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookService.Rotated;
import za.co.handyflow.platform.debtcollection.dto.CreateWebhookEndpointRequest;
import za.co.handyflow.platform.debtcollection.dto.WebhookCreatedResponse;
import za.co.handyflow.platform.debtcollection.dto.WebhookEndpointResponse;
import za.co.handyflow.platform.debtcollection.dto.WebhookLogResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Supervisor-only management of webhook endpoints. The token and signing secret appear in a response exactly once. */
@RestController
@RequestMapping("/api/v1/debtcollection/webhook-endpoints")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Webhook Endpoints", description = "Signed inbound endpoints for delivery status, replies and payments")
public class WebhookAdminController {

    private final WebhookService service;
    private final FeatureGuard featureGuard;

    @PostMapping
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Create an endpoint — the response contains the URL token and signing secret, shown only once")
    public ResponseEntity<ApiResponse<WebhookCreatedResponse>> create(@Valid @RequestBody CreateWebhookEndpointRequest req) {
        featureGuard.requireModule("debtcollection");
        Created c = service.create(TenantContext.getTenantIdAsObject(), req.name(), req.purpose(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Endpoint created — store the token and secret now",
                WebhookCreatedResponse.created(WebhookEndpointResponse.of(c.endpoint()), c.token(), c.secret())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<WebhookEndpointResponse>>> list() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                service.list(TenantContext.getTenantIdAsObject()).stream().map(WebhookEndpointResponse::of).toList()));
    }

    @PostMapping("/{id}/rotate-secret")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Issue a new signing secret (shown once). The old secret stops working immediately; the URL is unchanged")
    public ResponseEntity<ApiResponse<WebhookCreatedResponse>> rotate(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        Rotated r = service.rotateSecret(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Secret rotated — store it now",
                WebhookCreatedResponse.rotated(WebhookEndpointResponse.of(r.endpoint()), r.secret())));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> revoke(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Endpoint revoked",
                WebhookEndpointResponse.of(service.revoke(TenantContext.getTenantIdAsObject(), id))));
    }

    @GetMapping("/{id}/log")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Recent requests this endpoint received and what happened to them (no payloads)")
    public ResponseEntity<ApiResponse<List<WebhookLogResponse>>> log(@PathVariable UUID id,
                                                                     @RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                service.recentLog(TenantContext.getTenantIdAsObject(), id, limit).stream().map(WebhookLogResponse::of).toList()));
    }
}
