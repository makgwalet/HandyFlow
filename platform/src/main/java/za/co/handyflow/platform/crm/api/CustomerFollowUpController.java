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
import za.co.handyflow.platform.crm.application.internal.CustomerFollowUpService;
import za.co.handyflow.platform.crm.dto.CompleteFollowUpRequest;
import za.co.handyflow.platform.crm.dto.CreateFollowUpRequest;
import za.co.handyflow.platform.crm.dto.FollowUpResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** FIX: "no task/follow-up reminder system" gap. */
@RestController
@RequestMapping("/api/v1/crm/customers/{customerId}/followups")
@RequiredArgsConstructor
@Tag(name = "CRM - Follow-ups", description = "Scheduled follow-up reminders on a customer")
public class CustomerFollowUpController {

    private final CustomerFollowUpService followUpService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "List follow-ups for a customer, soonest due date first")
    public ResponseEntity<ApiResponse<List<FollowUpResponse>>> list(@PathVariable UUID customerId) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(followUpService.getForCustomer(tenantId, customerId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Schedule a follow-up on a customer")
    public ResponseEntity<ApiResponse<FollowUpResponse>> create(
            @PathVariable UUID customerId,
            @Valid @RequestBody CreateFollowUpRequest request) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var followUp = followUpService.create(tenantId, customerId, request, currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Follow-up scheduled", followUp));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Mark a follow-up done, recording its outcome — reschedules create a linked new follow-up")
    public ResponseEntity<ApiResponse<FollowUpResponse>> complete(
            @PathVariable UUID customerId,
            @PathVariable UUID id,
            @Valid @RequestBody CompleteFollowUpRequest request) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(followUpService.complete(tenantId, id, request, currentUserId())));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Undo a mistaken complete")
    public ResponseEntity<ApiResponse<FollowUpResponse>> reopen(
            @PathVariable UUID customerId,
            @PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(followUpService.reopen(tenantId, id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a follow-up")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID customerId,
            @PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        followUpService.delete(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Follow-up deleted", null));
    }

    /**
     * FIX: confirmed via real testing — @RequestAttribute("userId") threw
     * ServletRequestBindingException: nothing in this runtime's filter
     * chain actually populates that attribute, despite
     * CustomerConsentController.recordReview() (pre-existing code) using
     * the exact same pattern. Same resolution PopiaExportController
     * already uses successfully — the JWT subject is the user's UUID
     * string, read directly from SecurityContextHolder rather than an
     * unpopulated request attribute.
     */
    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        try { return UUID.fromString(auth.getName()); }
        catch (Exception e) { return null; }
    }
}