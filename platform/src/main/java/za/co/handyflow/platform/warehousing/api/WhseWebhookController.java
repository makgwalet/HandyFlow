package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseWebhookRules;
import za.co.handyflow.platform.warehousing.application.internal.WhseWebhookService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/webhooks")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Webhooks", description = "Send warehousing events to your own systems, signed and retried")
public class WhseWebhookController {

    private static final String ADMIN = "hasAuthority('WAREHOUSING_ADMIN')";

    private final WhseWebhookService service;
    private final FeatureGuard featureGuard;

    public record WebhookRequest(@NotNull @Size(min = 1, max = 120) String name, @NotNull @Size(max = 500) String url, @NotEmpty List<String> events, Boolean active) {}

    @GetMapping("/events")
    @PreAuthorize(ADMIN)
    @Operation(summary = "The events a webhook can listen for")
    public ResponseEntity<ApiResponse<List<String>>> events() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(WhseWebhookRules.EVENTS));
    }

    @GetMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<WhseWebhookService.Webhook>>> list() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    @Operation(summary = "Create a webhook. The signing secret is in the answer and is not shown again.")
    public ResponseEntity<ApiResponse<WhseWebhookService.Created>> create(@Valid @RequestBody WebhookRequest r) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(TenantContext.getTenantIdAsObject(), r.name(), r.url(), r.events(), TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<WhseWebhookService.Webhook>> update(@PathVariable UUID id, @Valid @RequestBody WebhookRequest r) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Saved", service.update(TenantContext.getTenantIdAsObject(), id, r.name(), r.url(), r.events(), r.active() == null || r.active(),
                TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.delete(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<Void>> resume(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.resume(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Resumed", null));
    }

    @PostMapping("/{id}/rotate-secret")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Replace the signing secret. The old one stops working at once.")
    public ResponseEntity<ApiResponse<WhseWebhookService.Created>> rotate(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Secret replaced", service.rotateSecret(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Send a ping now and report how it went")
    public ResponseEntity<ApiResponse<WhseWebhookService.TestResult>> test(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.test(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/deliveries")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Recent deliveries, newest first; status PENDING, RETRY, DELIVERED or DEAD narrows them")
    public ResponseEntity<ApiResponse<List<WhseWebhookService.Delivery>>> deliveries(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.deliveries(TenantContext.getTenantIdAsObject(), status, limit)));
    }

    @PostMapping("/deliveries/{id}/redeliver")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<Void>> redeliver(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.redeliver(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Queued", null));
    }
}
