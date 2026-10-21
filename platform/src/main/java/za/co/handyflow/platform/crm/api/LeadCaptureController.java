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
import za.co.handyflow.platform.crm.application.internal.LeadCaptureService;
import za.co.handyflow.platform.crm.dto.CaptureKeyRequest;
import za.co.handyflow.platform.crm.dto.CaptureKeyResponse;
import za.co.handyflow.platform.crm.dto.CreatedCaptureKeyResponse;
import za.co.handyflow.platform.crm.dto.LeadCaptureRequest;
import za.co.handyflow.platform.crm.dto.LeadCaptureResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/**
 * Lead capture. The public call at /api/v1/public/crm/leads has no login and no TenantContext: the X-Crm-Key header names the account,
 * and an unknown, missing or revoked key gets 401. That exact path is permitted in SecurityConfig and rate limited in RateLimitFilter.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "CRM - Integrations", description = "Send leads in from a website form or another system")
public class LeadCaptureController {
    private final LeadCaptureService service;

    @PostMapping("/api/v1/public/crm/leads")
    @Operation(summary = "Send in a lead. Header X-Crm-Key is a lead capture key. Answers CREATED, DUPLICATE (already on file) or IGNORED")
    public ResponseEntity<ApiResponse<LeadCaptureResponse>> capture(@RequestHeader(value = "X-Crm-Key", required = false) String key, @RequestBody LeadCaptureRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.capture(key, request)));
    }

    @GetMapping("/api/v1/crm/integrations/keys")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "The lead capture keys (never the keys themselves)")
    public ResponseEntity<ApiResponse<List<CaptureKeyResponse>>> keys() {
        return ResponseEntity.ok(ApiResponse.success(service.listKeys(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/api/v1/crm/integrations/keys")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Make a lead capture key. The key is shown once in this answer and cannot be shown again")
    public ResponseEntity<ApiResponse<CreatedCaptureKeyResponse>> createKey(@RequestBody CaptureKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createKey(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), request)));
    }

    @DeleteMapping("/api/v1/crm/integrations/keys/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Revoke a lead capture key. Takes effect at once and cannot be undone")
    public ResponseEntity<ApiResponse<CaptureKeyResponse>> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.revokeKey(TenantContext.getTenantIdAsObject(), id)));
    }
}
