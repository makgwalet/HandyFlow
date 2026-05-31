package za.co.handyflow.platform.desk.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.desk.domain.model.*;
import za.co.handyflow.platform.desk.domain.repository.*;
import za.co.handyflow.platform.desk.dto.*;
import za.co.handyflow.platform.shared.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeskService {

    private final DeskTicketRepository   ticketRepo;
    private final DeskCommentRepository  commentRepo;
    private final DeskCategoryRepository categoryRepo;
    private final DeskSlaPolicyRepository slaRepo;
    private final EmailService           emailService;
    private final JdbcTemplate           jdbc;

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DeskSummaryResponse getSummary(TenantId tenantId) {
        return new DeskSummaryResponse(
                ticketRepo.countByStatus(tenantId, "OPEN"),
                ticketRepo.countByStatus(tenantId, "IN_PROGRESS"),
                ticketRepo.countByStatus(tenantId, "WAITING_ON_CUSTOMER")
                        + ticketRepo.countByStatus(tenantId, "WAITING_ON_THIRD_PARTY"),
                ticketRepo.countByStatus(tenantId, "RESOLVED")
                        + ticketRepo.countByStatus(tenantId, "CLOSED"),
                countUrgentOpen(tenantId),
                ticketRepo.countSlaBreached(tenantId),
                countByChannel(tenantId, "HELPDESK"),
                countByChannel(tenantId, "INTERNAL")
        );
    }

    // ── Tickets ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TicketResponse> getTickets(TenantId tenantId, String status,
                                            String channel, String priority,
                                            Pageable pageable) {
        return ticketRepo.findAll(tenantId, status, channel, priority, pageable)
                .map(t -> toTicketResponse(t, false));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(TenantId tenantId, UUID id) {
        return toTicketResponse(findTicket(tenantId, id), true);
    }

    @Transactional
    public TicketResponse createTicket(TenantId tenantId, UUID createdBy,
                                        CreateTicketRequest req) {
        // Generate ticket number: TKT-0001, TKT-0002, ...
        int seq = ticketRepo.findMaxTicketSequence(tenantId) + 1;
        String ticketNumber = "TKT-%04d".formatted(seq);

        // Calculate SLA due date based on priority
        Instant dueAt = slaRepo.findByTenantIdAndPriority(tenantId,
                req.priority() != null ? req.priority() : "NORMAL")
                .map(sla -> sla.calculateDueAt(Instant.now()))
                .orElse(Instant.now().plusSeconds(72 * 3600)); // default 72h

        DeskTicket ticket = DeskTicket.create(
                tenantId, ticketNumber, req.channel(),
                req.requesterName(), req.requesterEmail(), req.requesterPhone(),
                req.customerId(), req.subject(), req.description(),
                req.categoryId(), req.priority(), dueAt, createdBy);

        if (req.assignedTo() != null) ticket.assign(req.assignedTo());
        ticketRepo.save(ticket);

        // Add system comment
        addSystemComment(ticket.getId(), tenantId.getValue(),
                "Ticket created by " + (createdBy != null ? "staff" : "customer portal"));

        // Notify assignee if assigned at creation
        if (req.assignedTo() != null) {
            notifyAssignee(ticket, req.assignedTo());
        }

        log.info("Created ticket={} priority={} channel={}", ticketNumber,
                req.priority(), req.channel());
        return toTicketResponse(ticket, false);
    }

    @Transactional
    public TicketResponse updateTicket(TenantId tenantId, UUID id, UpdateTicketRequest req) {
        DeskTicket ticket = findTicket(tenantId, id);
        ticket.updateDetails(req.subject(), req.description(),
                req.categoryId(), req.priority(), req.notes());
        ticketRepo.save(ticket);
        return toTicketResponse(ticket, false);
    }

    @Transactional
    public TicketResponse assignTicket(TenantId tenantId, UUID id, UUID assigneeId) {
        DeskTicket ticket = findTicket(tenantId, id);
        ticket.assign(assigneeId);
        ticketRepo.save(ticket);
        addSystemComment(ticket.getId(), tenantId.getValue(),
                "Ticket assigned to agent");
        notifyAssignee(ticket, assigneeId);
        return toTicketResponse(ticket, false);
    }

    @Transactional
    public TicketResponse updateStatus(TenantId tenantId, UUID id, String action) {
        DeskTicket ticket = findTicket(tenantId, id);
        switch (action.toUpperCase()) {
            case "START"           -> ticket.startProgress();
            case "WAIT_CUSTOMER"   -> ticket.waitOnCustomer();
            case "WAIT_THIRD_PARTY"-> ticket.waitOnThirdParty();
            case "RESOLVE"         -> { ticket.resolve(); notifyRequesterResolved(ticket); }
            case "CLOSE"           -> ticket.close();
            case "REOPEN"          -> ticket.reopen();
            default -> throw new HandyFlowException(
                    "Unknown action: " + action, HttpStatus.BAD_REQUEST, "INVALID_ACTION");
        }
        ticketRepo.save(ticket);
        addSystemComment(ticket.getId(), tenantId.getValue(),
                "Status changed to " + ticket.getStatus());
        return toTicketResponse(ticket, false);
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Transactional
    public TicketResponse addComment(TenantId tenantId, UUID id,
                                      DeskAddCommentRequest req, String staffName) {
        DeskTicket ticket = findTicket(tenantId, id);

        DeskComment comment = DeskComment.create(
                id, tenantId.getValue(), staffName, "TEAM",
                req.internal(), req.body());
        commentRepo.save(comment);

        // Record first response if this is the first team reply
        if (!req.internal()) {
            ticket.recordFirstResponse();
            ticketRepo.save(ticket);

            // Notify requester of reply
            if (ticket.getRequesterEmail() != null) {
                notifyRequesterReply(ticket, req.body(), staffName);
            }
        }

        return toTicketResponse(ticket, true);
    }

    // ── PUBLIC endpoints (no auth — customer portal) ──────────────────────────

    @Transactional
    public TicketResponse createPublicTicket(TenantId tenantId, CreateTicketRequest req) {
        return createTicket(tenantId, null, req);
    }

    @Transactional(readOnly = true)
    public PublicTicketResponse getTicketByToken(String token) {
        DeskTicket ticket = ticketRepo.findByPublicToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Ticket not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        // Only return non-internal comments to the customer
        List<DeskCommentResponse> comments = commentRepo
                .findByTicketIdAndIsInternalFalseOrderByCreatedAtAsc(ticket.getId())
                .stream().map(this::toCommentResponse).toList();

        return new PublicTicketResponse(
                ticket.getId(), ticket.getTicketNumber(),
                ticket.getSubject(), ticket.getStatus(), ticket.getPriority(),
                ticket.getCreatedAt(), ticket.getUpdatedAt(), comments);
    }

    @Transactional
    public void addCustomerComment(String token, DeskAddCommentRequest req) {
        DeskTicket ticket = ticketRepo.findByPublicToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Ticket not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        String authorName = req.authorName() != null ? req.authorName() : "Customer";
        DeskComment comment = DeskComment.create(
                ticket.getId(), ticket.getTenantId().getValue(),
                authorName, "CUSTOMER", false, req.body());
        commentRepo.save(comment);

        // Move ticket back to OPEN if it was waiting
        if ("WAITING_ON_CUSTOMER".equals(ticket.getStatus())) {
            ticket.startProgress();
            ticketRepo.save(ticket);
        }

        log.info("Customer comment added to ticket={}", ticket.getTicketNumber());
    }

    // ── Categories ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DeskCategoryResponse> getCategories(TenantId tenantId) {
        return categoryRepo.findByTenantIdAndActiveTrueOrderBySortOrderAsc(tenantId)
                .stream().map(c -> new DeskCategoryResponse(
                        c.getId(), c.getName(), c.getDescription(),
                        c.getColor(), c.getSortOrder()))
                .toList();
    }

    @Transactional
    public DeskCategoryResponse createCategory(TenantId tenantId, CreateCategoryRequest req) {
        DeskCategory cat = DeskCategory.create(tenantId, req.name(),
                req.description(), req.color(), req.sortOrder());
        categoryRepo.save(cat);
        return new DeskCategoryResponse(cat.getId(), cat.getName(),
                cat.getDescription(), cat.getColor(), cat.getSortOrder());
    }

    // ── SLA policies ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DeskSlaPolicy> getSlaPolocies(TenantId tenantId) {
        return slaRepo.findByTenantId(tenantId);
    }

    @Transactional
    public void updateSlaPolicies(TenantId tenantId, List<UpdateSlaPolicyRequest> policies) {
        for (UpdateSlaPolicyRequest req : policies) {
            slaRepo.findByTenantIdAndPriority(tenantId, req.priority())
                    .ifPresentOrElse(existing -> {
                        // Update via JDBC — no setters on entity
                        jdbc.update("""
                            UPDATE desk_sla_policies
                            SET first_response_hours = ?, resolution_hours = ?
                            WHERE id = ?
                            """, req.firstResponseHours(), req.resolutionHours(), existing.getId());
                    }, () -> {
                        DeskSlaPolicy policy = DeskSlaPolicy.create(tenantId,
                                req.priority(), req.firstResponseHours(), req.resolutionHours());
                        slaRepo.save(policy);
                    });
        }
    }

    // ── Scheduler: mark SLA breaches ─────────────────────────────────────────

    @Transactional
    public void checkSlaBreaches() {
        List<DeskTicket> breached = ticketRepo.findSlaBreaches(Instant.now());
        breached.forEach(ticket -> {
            ticket.markSlaBreached();
            ticketRepo.save(ticket);
            log.warn("SLA breached for ticket={}", ticket.getTicketNumber());
        });
        if (!breached.isEmpty()) {
            log.info("Marked {} tickets as SLA breached", breached.size());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DeskTicket findTicket(TenantId tenantId, UUID id) {
        return ticketRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id.toString()));
    }

    private void addSystemComment(UUID ticketId, UUID tenantId, String message) {
        DeskComment comment = DeskComment.create(
                ticketId, tenantId, "System", "SYSTEM", false, message);
        commentRepo.save(comment);
    }

    private void notifyAssignee(DeskTicket ticket, UUID assigneeId) {
        try {
            String email = jdbc.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, assigneeId);
            if (email != null) {
                emailService.send(email,
                        "Ticket assigned to you: " + ticket.getTicketNumber(),
                        "<p>You have been assigned ticket <strong>" + ticket.getTicketNumber()
                        + "</strong>: " + ticket.getSubject() + "</p>"
                        + "<p>Priority: <strong>" + ticket.getPriority() + "</strong></p>"
                        + "<p><a href=\"https://app.handyflow.co.za/desk/tickets/"
                        + ticket.getId() + "\">View ticket</a></p>");
            }
        } catch (Exception e) {
            log.warn("Could not notify assignee: {}", e.getMessage());
        }
    }

    private void notifyRequesterReply(DeskTicket ticket, String reply, String staffName) {
        try {
            String publicUrl = "https://app.handyflow.co.za/support/"
                    + ticket.getPublicToken();
            emailService.send(ticket.getRequesterEmail(),
                    "Update on your ticket: " + ticket.getTicketNumber(),
                    "<p>Hi " + ticket.getRequesterName() + ",</p>"
                    + "<p>" + staffName + " has replied to your support ticket.</p>"
                    + "<blockquote style=\"border-left:3px solid #0D9488;padding-left:12px\">"
                    + reply + "</blockquote>"
                    + "<p><a href=\"" + publicUrl + "\">View ticket and reply</a></p>");
        } catch (Exception e) {
            log.warn("Could not notify requester of reply: {}", e.getMessage());
        }
    }

    private void notifyRequesterResolved(DeskTicket ticket) {
        if (ticket.getRequesterEmail() == null) return;
        try {
            String publicUrl = "https://app.handyflow.co.za/support/"
                    + ticket.getPublicToken();
            emailService.send(ticket.getRequesterEmail(),
                    "Ticket resolved: " + ticket.getTicketNumber(),
                    "<p>Hi " + ticket.getRequesterName() + ",</p>"
                    + "<p>Your support ticket <strong>" + ticket.getTicketNumber()
                    + "</strong> has been resolved.</p>"
                    + "<p>If you're not satisfied with the resolution, you can reopen it:</p>"
                    + "<p><a href=\"" + publicUrl + "\">View and respond to ticket</a></p>");
        } catch (Exception e) {
            log.warn("Could not notify requester of resolution: {}", e.getMessage());
        }
    }

    private long countUrgentOpen(TenantId tenantId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM desk_tickets
                    WHERE tenant_id = ? AND priority = 'URGENT'
                    AND status NOT IN ('RESOLVED','CLOSED') AND deleted_at IS NULL
                    """, Long.class, tenantId.getValue());
        } catch (Exception e) { return 0; }
    }

    private long countByChannel(TenantId tenantId, String channel) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM desk_tickets
                    WHERE tenant_id = ? AND channel = ?
                    AND status NOT IN ('RESOLVED','CLOSED') AND deleted_at IS NULL
                    """, Long.class, tenantId.getValue(), channel);
        } catch (Exception e) { return 0; }
    }

    private TicketResponse toTicketResponse(DeskTicket t, boolean includeComments) {
        String categoryName = t.getCategoryId() != null
                ? fetchCategoryName(t.getCategoryId()) : null;
        String assignedToName = t.getAssignedTo() != null
                ? fetchUserName(t.getAssignedTo()) : null;

        List<DeskCommentResponse> comments = includeComments
                ? commentRepo.findByTicketIdOrderByCreatedAtAsc(t.getId())
                        .stream().map(this::toCommentResponse).toList()
                : List.of();

        return new TicketResponse(
                t.getId(), t.getTicketNumber(), t.getChannel(),
                t.getRequesterName(), t.getRequesterEmail(), t.getRequesterPhone(),
                t.getCustomerId(), t.getSubject(), t.getDescription(),
                t.getCategoryId(), categoryName,
                t.getPriority(), t.getStatus(),
                t.getAssignedTo(), assignedToName,
                t.isSlaBreached(), t.getDueAt(),
                t.getFirstResponseAt(), t.getResolvedAt(), t.getClosedAt(),
                t.getPublicToken(), t.getNotes(),
                comments, t.getCreatedAt(), t.getUpdatedAt());
    }

    private DeskCommentResponse toCommentResponse(DeskComment c) {
        return new DeskCommentResponse(c.getId(), c.getAuthorName(),
                c.getAuthorType(), c.isInternal(), c.getBody(), c.getCreatedAt());
    }

    private String fetchCategoryName(UUID categoryId) {
        try {
            return jdbc.queryForObject(
                    "SELECT name FROM desk_categories WHERE id = ?",
                    String.class, categoryId);
        } catch (Exception e) { return null; }
    }

    private String fetchUserName(UUID userId) {
        try {
            return jdbc.queryForObject(
                    "SELECT first_name || ' ' || last_name FROM users WHERE id = ?",
                    String.class, userId);
        } catch (Exception e) { return null; }
    }
}
