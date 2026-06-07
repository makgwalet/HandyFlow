package za.co.handyflow.platform.desk.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.desk.application.internal.DeskService;
import za.co.handyflow.platform.desk.domain.model.DeskSlaPolicy;
import za.co.handyflow.platform.desk.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Fixes applied:
 * 1. Removed JdbcTemplate injection — JdbcTemplate is a service-layer concern,
 *    not a controller concern. Moved resolveTenantBySlug into DeskService.
 * 2. updateStatus used @PatchMapping — replaced with @PostMapping to avoid CORS
 *    preflight failures on browsers that block PATCH.
 * 3. addComment: fetchCurrentUserName() hardcoded "Support Agent".
 *    Now delegates to TenantContext.getCurrentUserId() and lets DeskService
 *    look up the actual name from the users table.
 * 4. getSlaPolocies (typo in original) — kept as-is in service, fixed endpoint name here.
 * 5. Comments endpoint: original had /tickets/{id}/comments returning List but
 *    the frontend called it expecting comments inside the TicketResponse.
 *    The service already returns TicketResponse with embedded comments when
 *    includeComments=true. Controller now returns TicketResponse consistently.
 */
@RestController
@RequestMapping("/api/v1/desk")
@RequiredArgsConstructor
@Tag(name = "Desk Support", description = "Support ticket system — internal and customer helpdesk")
public class DeskController {

    private final DeskService deskService;

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DESK_READ')")
    @Operation(summary = "Desk dashboard — ticket counts by status, SLA breaches")
    public ResponseEntity<ApiResponse<DeskSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                deskService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Tickets ───────────────────────────────────────────────────────────────

    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('DESK_READ')")
    @Operation(summary = "List tickets — filter by status, channel (INTERNAL|HELPDESK), priority")
    public ResponseEntity<ApiResponse<Page<TicketResponse>>> getTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String priority,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                deskService.getTickets(TenantContext.getTenantIdAsObject(),
                        status, channel, priority, pageable)));
    }

    @GetMapping("/tickets/{id}")
    @PreAuthorize("hasAuthority('DESK_READ')")
    @Operation(summary = "Get ticket detail with full comment thread")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                deskService.getTicket(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/tickets")
    @PreAuthorize("hasAuthority('DESK_MANAGE')")
    @Operation(summary = "Create a support ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            @Valid @RequestBody CreateTicketRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Ticket created",
                deskService.createTicket(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/tickets/{id}")
    @PreAuthorize("hasAuthority('DESK_MANAGE')")
    @Operation(summary = "Update ticket details")
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicket(
            @PathVariable UUID id,
            @RequestBody UpdateTicketRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Ticket updated",
                deskService.updateTicket(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/tickets/{id}/assign/{userId}")
    @PreAuthorize("hasAuthority('DESK_MANAGE')")
    @Operation(summary = "Assign ticket to a team member")
    public ResponseEntity<ApiResponse<TicketResponse>> assignTicket(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Ticket assigned",
                deskService.assignTicket(TenantContext.getTenantIdAsObject(), id, userId)));
    }

    // FIX: was @PatchMapping — browsers block PATCH in cross-origin requests.
    // The service already uses action strings (START, RESOLVE, etc.) so the
    // HTTP method semantics were wrong anyway. POST /action/{action} is correct.
    @PostMapping("/tickets/{id}/action/{action}")
    @PreAuthorize("hasAuthority('DESK_MANAGE')")
    @Operation(summary = "Update ticket status — action: START | WAIT_CUSTOMER | WAIT_THIRD_PARTY | RESOLVE | CLOSE | REOPEN")
    public ResponseEntity<ApiResponse<TicketResponse>> updateStatus(
            @PathVariable UUID id,
            @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                deskService.updateStatus(TenantContext.getTenantIdAsObject(), id, action)));
    }

    // FIX: returns TicketResponse (with embedded comments) not bare List.
    // The original returned a TicketResponse but the frontend was calling
    // a separate /comments endpoint that didn't exist — comments are embedded
    // in the TicketResponse when includeComments=true (the getTicket endpoint).
    @PostMapping("/tickets/{id}/comments")
    @PreAuthorize("hasAuthority('DESK_MANAGE')")
    @Operation(summary = "Add a comment or internal note to a ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody DeskAddCommentRequest req) {
        // FIX: was hardcoded "Support Agent". Now resolves actual user name.
        return ResponseEntity.ok(ApiResponse.success("Comment added",
                deskService.addComment(TenantContext.getTenantIdAsObject(),
                        id, req, TenantContext.getCurrentUserId())));
    }

    // ── Categories ────────────────────────────────────────────────────────────

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('DESK_READ')")
    @Operation(summary = "List ticket categories")
    public ResponseEntity<ApiResponse<List<DeskCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(
                deskService.getCategories(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('DESK_ADMIN')")
    @Operation(summary = "Create a ticket category")
    public ResponseEntity<ApiResponse<DeskCategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Category created",
                deskService.createCategory(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── SLA policies ──────────────────────────────────────────────────────────

    @GetMapping("/sla")
    @PreAuthorize("hasAuthority('DESK_READ')")
    @Operation(summary = "Get SLA policies per priority level")
    public ResponseEntity<ApiResponse<List<DeskSlaPolicy>>> getSla() {
        return ResponseEntity.ok(ApiResponse.success(
                deskService.getSlaPolocies(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/sla")
    @PreAuthorize("hasAuthority('DESK_ADMIN')")
    @Operation(summary = "Update SLA policies — send all 4 priority levels")
    public ResponseEntity<ApiResponse<Void>> updateSla(
            @Valid @RequestBody List<UpdateSlaPolicyRequest> policies) {
        deskService.updateSlaPolicies(TenantContext.getTenantIdAsObject(), policies);
        return ResponseEntity.ok(ApiResponse.success("SLA policies updated", null));
    }

    // ── PUBLIC endpoints — customer portal (no auth, token in URL) ────────────
    // SecurityConfig must have /api/v1/desk/portal/** in permitAll()

    @PostMapping("/portal/{tenantSlug}/tickets")
    @Operation(summary = "PUBLIC — Customer submits a support ticket via public portal")
    public ResponseEntity<ApiResponse<TicketResponse>> submitPublicTicket(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateTicketRequest req) {
        // FIX: slug resolution moved into DeskService — controllers should not
        // hold JdbcTemplate. Service already has jdbc injected for other queries.
        return ResponseEntity.status(201).body(ApiResponse.success("Ticket submitted",
                deskService.createPublicTicketBySlug(tenantSlug, req)));
    }

    @GetMapping("/portal/tickets/{token}")
    @Operation(summary = "PUBLIC — Customer tracks their ticket by token")
    public ResponseEntity<ApiResponse<PublicTicketResponse>> trackTicket(
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                deskService.getTicketByToken(token)));
    }

    @PostMapping("/portal/tickets/{token}/comments")
    @Operation(summary = "PUBLIC — Customer replies to their ticket")
    public ResponseEntity<ApiResponse<Void>> customerReply(
            @PathVariable String token,
            @Valid @RequestBody DeskAddCommentRequest req) {
        deskService.addCustomerComment(token, req);
        return ResponseEntity.ok(ApiResponse.success("Reply submitted", null));
    }
}
