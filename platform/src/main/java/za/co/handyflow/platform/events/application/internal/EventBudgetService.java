package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventBudget;
import za.co.handyflow.platform.events.domain.model.EventBudgetLine;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Planned budget, its approval workflow, and the planned-vs-actual summary.
 * <p>
 * LOCKING: every mutating method takes the event row lock first. That
 * serialises budget changes with each other (so two people approving
 * commitments at once can't both slip under the same line's limit) and gives
 * a safe place to create the one-per-event budget header without a race.
 * The lock is held only for a short transaction, so it does not meaningfully
 * delay guest registration.
 * <p>
 * APPROVAL: lines can only change while the budget is DRAFT. The approver
 * can be required to differ from the submitter by setting
 * app.events.budget.require-separate-approver=true (off by default so a
 * one-person business isn't locked out of its own budget).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventBudgetService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final EventRepository           eventRepo;
    private final EventBudgetRepository     budgetRepo;
    private final EventBudgetLineRepository lineRepo;
    private final EventCommitmentRepository commitmentRepo;
    private final EventCostEntryRepository  entryRepo;
    private final EventGuestRepository      guestRepo;
    private final EventAuditService         audit;

    @Value("${app.events.budget.require-separate-approver:false}")
    private boolean requireSeparateApprover;

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BudgetStatusResponse getStatus(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        return toStatus(budgetRepo.findByEvent(eventId), lineRepo.findByEvent(eventId));
    }

    @Transactional(readOnly = true)
    public List<BudgetLineResponse> listLines(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        return lineRepo.findByEvent(eventId).stream().map(this::toLineResponse).toList();
    }

    /** Planned vs committed vs invoiced vs paid, per line and in total, with alerts. */
    @Transactional(readOnly = true)
    public BudgetSummaryResponse getSummary(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        String status = budgetRepo.findByEvent(eventId).map(EventBudget::getStatus).orElse("NOT_STARTED");
        return BudgetCalculator.summarize(status,
                lineRepo.findByEvent(eventId),
                commitmentRepo.findByEvent(eventId),
                entryRepo.findActiveByEvent(eventId),
                LocalDate.now(SAST),
                guestRepo.countActive(eventId),
                guestRepo.countCheckedIn(eventId));
    }

    // ── Lines (DRAFT budgets only) ────────────────────────────────────────────

    @Transactional
    public BudgetLineResponse addLine(TenantId tenantId, UUID eventId, BudgetLineRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        assertEditable(getOrCreateHeader(tenantId, eventId));

        EventBudgetLine line = EventBudgetLine.create(tenantId, eventId, req.category(),
                req.description(), req.plannedAmount(), req.notes());
        lineRepo.save(line);
        audit.record(tenantId, eventId, "BUDGET_LINE_ADDED", "BUDGET_LINE", line.getId(),
                "Budget line added: " + line.getDescription() + " (" + line.getCategory() + ") "
                        + money(line.getPlannedAmount()));
        return toLineResponse(line);
    }

    @Transactional
    public BudgetLineResponse updateLine(TenantId tenantId, UUID eventId, UUID lineId, BudgetLineRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        assertEditable(getOrCreateHeader(tenantId, eventId));

        EventBudgetLine line = findLine(eventId, lineId);
        BigDecimal before = line.getPlannedAmount();
        line.update(req.category(), req.description(), req.plannedAmount(), req.notes());
        lineRepo.save(line);
        audit.record(tenantId, eventId, "BUDGET_LINE_UPDATED", "BUDGET_LINE", lineId,
                "Budget line updated: " + line.getDescription() + " (" + line.getCategory() + ")",
                List.of("planned: " + money(before) + " -> " + money(line.getPlannedAmount())));
        return toLineResponse(line);
    }

    /** A line that commitments or cost entries refer to can't be deleted — they would lose their budget home. */
    @Transactional
    public void deleteLine(TenantId tenantId, UUID eventId, UUID lineId) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        assertEditable(getOrCreateHeader(tenantId, eventId));

        EventBudgetLine line = findLine(eventId, lineId);
        if (commitmentRepo.countByLine(lineId) > 0 || entryRepo.countByLine(lineId) > 0) {
            throw new HandyFlowException(
                    "This line has supplier commitments or cost entries against it and can't be deleted",
                    HttpStatus.CONFLICT, "LINE_IN_USE");
        }
        lineRepo.delete(line);
        audit.record(tenantId, eventId, "BUDGET_LINE_DELETED", "BUDGET_LINE", lineId,
                "Budget line deleted: " + line.getDescription() + " (" + line.getCategory() + ") "
                        + money(line.getPlannedAmount()));
    }

    // ── Approval workflow ─────────────────────────────────────────────────────

    @Transactional
    public BudgetStatusResponse submit(TenantId tenantId, UUID eventId) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        EventBudget budget = getOrCreateHeader(tenantId, eventId);
        List<EventBudgetLine> lines = lineRepo.findByEvent(eventId);
        if (lines.isEmpty()) {
            throw new HandyFlowException("Add at least one budget line before submitting",
                    HttpStatus.CONFLICT, "BUDGET_EMPTY");
        }
        budget.submit(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        budgetRepo.save(budget);
        audit.record(tenantId, eventId, "BUDGET_SUBMITTED", "BUDGET", budget.getId(),
                "Budget submitted for approval — planned total " + money(plannedTotal(lines)));
        return toStatus(Optional.of(budget), lines);
    }

    @Transactional
    public BudgetStatusResponse approve(TenantId tenantId, UUID eventId) {
        lockEvent(tenantId, eventId);
        EventBudget budget = requireHeader(eventId);
        UUID approver = TenantContext.getCurrentUserId();

        if (requireSeparateApprover && approver.equals(budget.getSubmittedBy())) {
            throw new HandyFlowException("The person who submitted the budget can't also approve it",
                    HttpStatus.FORBIDDEN, "SELF_APPROVAL_NOT_ALLOWED");
        }
        List<EventBudgetLine> lines = lineRepo.findByEvent(eventId);
        BigDecimal total = plannedTotal(lines);

        budget.approve(approver, TenantContext.getCurrentUserName(), total);
        budgetRepo.save(budget);
        audit.record(tenantId, eventId, "BUDGET_APPROVED", "BUDGET", budget.getId(),
                "Budget approved — planned total " + money(total));
        return toStatus(Optional.of(budget), lines);
    }

    @Transactional
    public BudgetStatusResponse reject(TenantId tenantId, UUID eventId, String reason) {
        lockEvent(tenantId, eventId);
        EventBudget budget = requireHeader(eventId);
        budget.reject(reason.trim());
        budgetRepo.save(budget);
        audit.record(tenantId, eventId, "BUDGET_REJECTED", "BUDGET", budget.getId(),
                "Budget sent back for changes — " + reason.trim());
        return toStatus(Optional.of(budget), lineRepo.findByEvent(eventId));
    }

    /**
     * Unlocks a submitted or approved budget so its lines can change. The
     * previous approval is cleared; it has to be submitted and approved again.
     * Approved supplier commitments are NOT affected.
     */
    @Transactional
    public BudgetStatusResponse reopen(TenantId tenantId, UUID eventId, String reason) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        EventBudget budget = requireHeader(eventId);
        String previous = budget.getStatus();
        budget.reopen();
        budgetRepo.save(budget);
        audit.record(tenantId, eventId, "BUDGET_REOPENED", "BUDGET", budget.getId(),
                "Budget reopened for editing (was " + previous + ") — " + reason.trim());
        return toStatus(Optional.of(budget), lineRepo.findByEvent(eventId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Event findEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private Event lockEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveByIdForUpdate(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    /** Planning (lines, quotes, approvals) stops once an event is over or cancelled; recording actual costs does not. */
    private void assertPlanningOpen(Event event) {
        if ("COMPLETED".equals(event.getStatus()) || "CANCELLED".equals(event.getStatus())) {
            throw new HandyFlowException(
                    "Budget planning is closed for a " + event.getStatus() + " event — only cost entries can still be recorded",
                    HttpStatus.CONFLICT, "EVENT_CLOSED");
        }
    }

    private void assertEditable(EventBudget budget) {
        if (!budget.isEditable()) {
            throw new HandyFlowException(
                    "The budget is " + budget.getStatus() + " and its lines are locked — reopen it to make changes",
                    HttpStatus.CONFLICT, "BUDGET_LOCKED");
        }
    }

    /** Safe to create here: the caller holds the event row lock, so two requests can't both insert a header. */
    private EventBudget getOrCreateHeader(TenantId tenantId, UUID eventId) {
        return budgetRepo.findByEvent(eventId)
                .orElseGet(() -> budgetRepo.save(EventBudget.create(tenantId, eventId)));
    }

    private EventBudget requireHeader(UUID eventId) {
        return budgetRepo.findByEvent(eventId)
                .orElseThrow(() -> new HandyFlowException("This event has no budget yet",
                        HttpStatus.CONFLICT, "BUDGET_NOT_STARTED"));
    }

    private EventBudgetLine findLine(UUID eventId, UUID lineId) {
        return lineRepo.findByIdAndEvent(lineId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget line", lineId.toString()));
    }

    private BigDecimal plannedTotal(List<EventBudgetLine> lines) {
        return lines.stream().map(EventBudgetLine::getPlannedAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private BudgetStatusResponse toStatus(Optional<EventBudget> header, List<EventBudgetLine> lines) {
        BigDecimal total = plannedTotal(lines);
        if (header.isEmpty()) {
            return new BudgetStatusResponse("NOT_STARTED", lines.size(), total, null, null, null, null, null, null);
        }
        EventBudget b = header.get();
        return new BudgetStatusResponse(b.getStatus(), lines.size(), total, b.getSubmittedByName(),
                b.getSubmittedAt(), b.getApprovedByName(), b.getApprovedAt(), b.getApprovedTotal(),
                b.getRejectionReason());
    }

    private BudgetLineResponse toLineResponse(EventBudgetLine l) {
        return new BudgetLineResponse(l.getId(), l.getCategory(), l.getDescription(), l.getPlannedAmount(), l.getNotes());
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "R%,.2f", amount);
    }
}