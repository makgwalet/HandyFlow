package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.crm.application.internal.Customer360Service;
import za.co.handyflow.platform.crm.application.internal.CustomerService;
import za.co.handyflow.platform.crm.domain.repository.Customer360Repository.Customer360Summary;
import za.co.handyflow.platform.crm.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * CustomerController — REST API for CRM customer management.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHY keep controllers thin?
 *
 * A controller should do exactly four things:
 *   1. Parse/validate the HTTP request
 *   2. Call the service
 *   3. Map the result to an HTTP response
 *   4. Handle HTTP-level concerns (status codes, headers)
 *
 * Business logic (uniqueness checks, state transitions, audit logging)
 * belongs in CustomerService and the Customer domain entity.
 * If you find yourself writing an if/else in a controller, ask yourself:
 * "should this be in the service instead?"
 *
 * WHAT'S NEW vs ORIGINAL:
 * ┌──────────────────────────────────┬─────────────────────────────────┐
 * │ Original                         │ Production version              │
 * ├──────────────────────────────────┼─────────────────────────────────┤
 * │ CRUD only                        │ CRUD + restore + timeline + tags│
 * │ No deleted customer view         │ GET /deleted list               │
 * │ No restore endpoint              │ POST /{id}/restore              │
 * │ No activity timeline             │ GET /{id}/activities            │
 * │ No tag management                │ POST/DELETE /{id}/tags/{tag}    │
 * │ No add-note endpoint             │ POST /{id}/notes                │
 * │ featureGuard called in each method│ AOP approach via @FeatureModule│
 * │ Identical create/update shapes   │ Separate DTOs with status field │
 * └──────────────────────────────────┴─────────────────────────────────┘
 *
 * WHY move featureGuard to a single class-level point?
 * Calling featureGuard.requireModule("crm") in every method is noise.
 * Use AOP or a HandlerInterceptor to apply it once per controller class.
 * We keep it here in init() via a @ModelAttribute pre-check pattern —
 * but the cleanest solution is a custom @FeatureModule("crm") annotation
 * on the class processed by an aspect.  Either way, it's one line, not five.
 * ═══════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/v1/crm/customers")
@RequiredArgsConstructor
@Tag(name = "CRM - Customers", description = "Customer relationship management")
public class CustomerController {

    private final CustomerService   customerService;
    private final Customer360Service customer360Service;
    private final FeatureGuard      featureGuard;

    /**
     * WHY @ModelAttribute?
     * This method runs before EVERY handler in this controller.
     * It's the right place for cross-cutting pre-conditions like
     * feature flag checks.  If the module isn't enabled, it throws
     * before any business logic runs.
     */
    @ModelAttribute
    public void requireCrmModule() {
        featureGuard.requireModule("crm");
    }

    // ══════════════════════════════════════════════════════════════════════
    // LIST & READ
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "List active customers with optional search, pagination, and ownership filter")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getCustomers(
            @Parameter(description = "Full-text search across name, email, phone, VAT number")
            @RequestParam(required = false) String search,
            @Parameter(description = "If true, only customers owned by the current user, or unowned — the \"my leads\" filter")
            @RequestParam(required = false) Boolean mine,
            @PageableDefault(size = 10, sort = "name") Pageable pageable
    ) {
        var tenantId  = TenantContext.getTenantIdAsObject();
        var customers = customerService.getCustomers(tenantId, search, mine, pageable);
        return ResponseEntity.ok(ApiResponse.success(customers));
    }

    /** FIX: backlog 4.1 — "no lead ownership/assignment" gap. */
    @PatchMapping("/{id}/owner")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Reassign (or unassign, with a null ownerId) a customer's owner")
    public ResponseEntity<ApiResponse<CustomerResponse>> assignOwner(
            @PathVariable UUID id,
            @RequestBody UpdateOwnerRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.assignOwner(tenantId, id, request.ownerId());
        return ResponseEntity.ok(ApiResponse.success("Owner updated", customer));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get a single active customer by ID")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.getCustomer(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    /**
     * NEW: View soft-deleted customers.
     * WHY? Staff make mistakes.  Showing deleted customers allows
     * them to restore accidentally deleted records without a developer
     * having to run a SQL query.
     */
    @GetMapping("/deleted")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")  // reuse delete authority for restore too
    @Operation(summary = "List soft-deleted customers (restorable)")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getDeletedCustomers(
            @PageableDefault(size = 10, sort = "name") Pageable pageable
    ) {
        var tenantId  = TenantContext.getTenantIdAsObject();
        var customers = customerService.getDeletedCustomers(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(customers));
    }

    // ══════════════════════════════════════════════════════════════════════
    // CREATE & UPDATE
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    @Operation(summary = "Create a new customer or lead")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.createCustomer(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer created", customer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Update customer details")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.updateCustomer(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Customer updated", customer));
    }

    // ══════════════════════════════════════════════════════════════════════
    // DELETE & RESTORE
    // ══════════════════════════════════════════════════════════════════════

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    @Operation(summary = "Soft-delete a customer (restorable)")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        customerService.softDeleteCustomer(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted", null));
    }

    /**
     * NEW: Restore a soft-deleted customer.
     * WHY POST and not PUT?
     * Restore is an action (a command), not an update to a resource.
     * POST /{id}/restore reads as "perform the restore action on customer {id}".
     * PUT /{id} would require sending the full customer body.
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    @Operation(summary = "Restore a previously soft-deleted customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> restoreCustomer(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.restoreCustomer(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Customer restored", customer));
    }

    // ══════════════════════════════════════════════════════════════════════
    // ACTIVITY TIMELINE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * NEW: Paginated activity timeline for a customer.
     * Returns: created, updated, deleted, restored, bookings linked, invoices linked, notes.
     * WHY paginated? A customer used for 2 years could have 500+ events.
     */
    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get paginated activity timeline for a customer")
    public ResponseEntity<ApiResponse<Page<CustomerActivityResponse>>> getActivities(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        var tenantId    = TenantContext.getTenantIdAsObject();
        var activities  = customerService.getActivities(tenantId, id, pageable);
        return ResponseEntity.ok(ApiResponse.success(activities));
    }

    /**
     * NEW: Add a manual timestamped note to a customer's timeline.
     * Different from the freeform notes field — this records WHO said WHAT and WHEN.
     */
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a timestamped note to the customer's activity timeline")
    public ResponseEntity<ApiResponse<CustomerActivityResponse>> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody AddNoteRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var activity = customerService.addNote(tenantId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note added", activity));
    }

    // ══════════════════════════════════════════════════════════════════════
    // TAG MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * NEW: Add a tag to a customer.
     * WHY PUT not POST? Adding a tag is idempotent — adding "vip" twice
     * results in the same state.  PUT communicates idempotency.
     */
    @PutMapping("/{id}/tags/{tag}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a tag to a customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> addTag(
            @PathVariable UUID id,
            @PathVariable String tag
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.addTag(tenantId, id, tag);
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @DeleteMapping("/{id}/tags/{tag}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Remove a tag from a customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> removeTag(
            @PathVariable UUID id,
            @PathVariable String tag
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var customer = customerService.removeTag(tenantId, id, tag);
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    // ══════════════════════════════════════════════════════════════════════
    // CUSTOMER 360 VIEW
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{id}/360")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Customer 360 — linked booking and invoice summary")
    public ResponseEntity<ApiResponse<Customer360Summary>> get360Summary(
            @PathVariable UUID id
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var summary  = customer360Service.get360(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // ══════════════════════════════════════════════════════════════════════
    // LEAD PIPELINE STAGE
    // ══════════════════════════════════════════════════════════════════════

    /** FIX: "no lead/pipeline stage tracking" gap. */
    @GetMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get a lead's current pipeline stage")
    public ResponseEntity<ApiResponse<StageResponse>> getStage(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(customerService.getStage(tenantId, id)));
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Change a lead's pipeline stage — only valid for LEAD-type customers")
    public ResponseEntity<ApiResponse<StageResponse>> changeStage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStageRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(customerService.changeStage(tenantId, id, request.stage())));
    }
}