package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CustomerCommunicationService;
import za.co.handyflow.platform.crm.dto.CommunicationResponse;
import za.co.handyflow.platform.crm.dto.LogCommunicationRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * FIX: "no email/communication log" gap.
 * <p>
 * NOTE: uses CUSTOMER_UPDATE (not CUSTOMER_WRITE) and resolves the current
 * user via SecurityContextHolder (not @RequestAttribute) — both confirmed
 * via real testing on the sibling consent/follow-up endpoints as the
 * patterns that actually work in this runtime, not the ones that merely
 * compiled.
 */
@RestController
@RequestMapping("/api/v1/crm/customers/{customerId}/communications")
@RequiredArgsConstructor
@Tag(name = "CRM - Communications", description = "Email/call/meeting log with a customer")
public class CustomerCommunicationController {

    private final CustomerCommunicationService communicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "List communication history for a customer, most recent first")
    public ResponseEntity<ApiResponse<List<CommunicationResponse>>> list(@PathVariable UUID customerId) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(communicationService.getForCustomer(tenantId, customerId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Log a call, email, meeting, or other communication with a customer")
    public ResponseEntity<ApiResponse<CommunicationResponse>> log(
            @PathVariable UUID customerId,
            @Valid @RequestBody LogCommunicationRequest request) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var result   = communicationService.log(tenantId, customerId, request, currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Communication logged", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a logged communication (e.g. logged in error)")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID customerId,
            @PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        communicationService.delete(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Communication deleted", null));
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        try { return UUID.fromString(auth.getName()); }
        catch (Exception e) { return null; }
    }
}