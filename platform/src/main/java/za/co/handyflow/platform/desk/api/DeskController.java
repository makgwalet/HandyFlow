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

@RestController
@RequestMapping("/api/v1/desk")
@RequiredArgsConstructor
@Tag(name = "Desk Support", description = "Support ticket system — internal and customer helpdesk")
public class DeskController {

    private final DeskService                          deskService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

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

    @PostMapping("/tickets/{id}/action/{action}")
    @PreAuthorize("hasAuthority('DESK_MANAGE')")
    @Operation(summary = "Update ticket status — action: START | WAIT_CUSTOMER | WAIT_THIRD_PARTY | RESOLVE | CLOSE | REOPEN")
    public ResponseEntity<ApiResponse<TicketResponse>> updateStatus(
            @PathVariable UUID id,
            @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                deskService.updateStatus(TenantContext.getTenantIdAsObject(), id, action)));
    }

    @PostMapping("/tickets/{id}/comments")
    @PreAuthorize("hasAuthority('DESK_MANAGE')")
    @Operation(summary = "Add a comment or internal note to a ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody DeskAddCommentRequest req) {
        String staffName = fetchCurrentUserName();
        return ResponseEntity.ok(ApiResponse.success("Comment added",
                deskService.addComment(TenantContext.getTenantIdAsObject(),
                        id, req, staffName)));
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
        TenantId tenantId = resolveTenantBySlug(tenantSlug);
        return ResponseEntity.status(201).body(ApiResponse.success("Ticket submitted",
                deskService.createPublicTicket(tenantId, req)));
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fetchCurrentUserName() {
        return "Support Agent"; // TODO wire to UserRepository via TenantContext.getCurrentUserId()
    }

    private TenantId resolveTenantBySlug(String slug) {
        try {
            String tenantIdStr = jdbc.queryForObject(
                    "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug);
            return TenantId.of(tenantIdStr);
        } catch (Exception e) {
            throw new za.co.handyflow.platform.shared.HandyFlowException(
                    "Company not found: " + slug,
                    org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }
}
