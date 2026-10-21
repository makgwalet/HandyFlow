package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.WebhookService;
import za.co.handyflow.platform.crm.dto.CreatedWebhookResponse;
import za.co.handyflow.platform.crm.dto.WebhookDeliveryResponse;
import za.co.handyflow.platform.crm.dto.WebhookRequest;
import za.co.handyflow.platform.crm.dto.WebhookResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/integrations/webhooks")
@RequiredArgsConstructor
@Tag(name = "CRM - Integrations", description = "Signed messages to your own systems when CRM events happen")
public class WebhookController {
    private final WebhookService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "The webhooks (never their secrets)")
    public ResponseEntity<ApiResponse<List<WebhookResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a webhook. The signing secret is shown once in this answer. Each message carries X-HandyFlow-Signature: sha256=<HMAC-SHA256 of the body with the secret>")
    public ResponseEntity<ApiResponse<CreatedWebhookResponse>> create(@RequestBody WebhookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Change a webhook. Saving it also clears a suspension")
    public ResponseEntity<ApiResponse<WebhookResponse>> update(@PathVariable UUID id, @RequestBody WebhookRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a webhook")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Send a test message now and say how it went")
    public ResponseEntity<ApiResponse<WebhookDeliveryResponse>> test(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.test(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "The most recent attempts (default 50, at most 200; kept for 30 days)")
    public ResponseEntity<ApiResponse<List<WebhookDeliveryResponse>>> deliveries(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(service.recentDeliveries(TenantContext.getTenantIdAsObject(), limit)));
    }
}
