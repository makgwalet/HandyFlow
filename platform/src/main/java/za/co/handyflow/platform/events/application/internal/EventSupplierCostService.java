package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.*;
import za.co.handyflow.platform.events.domain.repository.*;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Supplier quotes and commitments, and the invoices/payments recorded against them.
 * <p>
 * THE CONTROLS THIS ENFORCES
 * <ul>
 *   <li>A commitment can only be APPROVED once the event's budget is APPROVED.</li>
 *   <li>Approving a commitment that would push its budget line's forecast above the
 *       planned amount is refused unless the approver gives an override reason, which
 *       is kept on the commitment and in the audit trail.</li>
 *   <li>After approval the amount, budget line and supplier are locked.</li>
 *   <li>Payments against a commitment can't exceed what is committed or invoiced.</li>
 *   <li>The same invoice (kind + amount + reference) or the same accounting record
 *       can't be entered twice.</li>
 *   <li>Cost entries are never deleted, only voided with a reason.</li>
 * </ul>
 * Every mutation locks the event row first, so concurrent approvals can't both
 * pass the "stays within budget" check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventSupplierCostService {

    private final EventRepository           eventRepo;
    private final EventBudgetRepository     budgetRepo;
    private final EventBudgetLineRepository lineRepo;
    private final EventCommitmentRepository commitmentRepo;
    private final EventCostEntryRepository  entryRepo;
    private final EventVendorRepository     vendorRepo;
    private final EventAuditService         audit;

    // ── Commitments: read ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommitmentResponse> listCommitments(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        List<EventCostEntry> active = entryRepo.findActiveByEvent(eventId);
        Map<UUID, EventBudgetLine> lines = lineMap(eventId);
        Map<UUID, String> vendors = vendorNames(eventId);
        return commitmentRepo.findByEvent(eventId).stream()
                .map(c -> toResponse(c, lines, vendors, active))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupplierSummary> supplierSummary(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        return BudgetCalculator.bySupplier(commitmentRepo.findByEvent(eventId),
                entryRepo.findActiveByEvent(eventId), vendorNames(eventId));
    }

    // ── Commitments: write ────────────────────────────────────────────────────

    @Transactional
    public CommitmentResponse createCommitment(TenantId tenantId, UUID eventId, CommitmentRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        requireLine(eventId, req.budgetLineId());
        requireVendor(eventId, req.vendorId());

        EventCommitment c = EventCommitment.create(tenantId, eventId, req.budgetLineId(), req.vendorId(),
                req.description(), req.amount(), req.quoteReference(), req.poReference(),
                req.contractReference(), req.deliverable(), req.dueDate(), req.depositAmount(),
                req.depositDueDate(), currentUserId());
        commitmentRepo.save(c);
        audit.record(tenantId, eventId, "COMMITMENT_QUOTED", "COMMITMENT", c.getId(),
                "Supplier quote recorded: " + c.getDescription() + " " + money(c.getAmount()));
        return respond(c, eventId);
    }

    /**
     * A QUOTED commitment can be edited freely. Once approved, only references,
     * the deliverable, dates and the deposit schedule may change — the agreed
     * amount, budget line and supplier are locked.
     */
    @Transactional
    public CommitmentResponse updateCommitment(TenantId tenantId, UUID eventId, UUID commitmentId,
                                               CommitmentRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        EventCommitment c = findCommitment(eventId, commitmentId);

        if (EventCommitment.QUOTED.equals(c.getStatus())) {
            requireLine(eventId, req.budgetLineId());
            requireVendor(eventId, req.vendorId());
            c.revise(req.budgetLineId(), req.vendorId(), req.description(), req.amount(),
                    req.quoteReference(), req.poReference(), req.contractReference(), req.deliverable(),
                    req.dueDate(), req.depositAmount(), req.depositDueDate());
        } else {
            boolean lockedFieldChanged = c.getAmount().compareTo(req.amount().setScale(2, RoundingMode.HALF_UP)) != 0
                    || !c.getBudgetLineId().equals(req.budgetLineId())
                    || !Objects.equals(c.getVendorId(), req.vendorId())
                    || !c.getDescription().equals(req.description().trim());
            if (lockedFieldChanged) {
                throw new HandyFlowException(
                        "An approved commitment's description, amount, budget line and supplier can't be changed — "
                                + "cancel it and raise a new one",
                        HttpStatus.CONFLICT, "COMMITMENT_LOCKED");
            }
            c.updateAdministrative(req.quoteReference(), req.poReference(), req.contractReference(),
                    req.deliverable(), req.dueDate(), req.depositAmount(), req.depositDueDate());
        }
        commitmentRepo.save(c);
        audit.record(tenantId, eventId, "COMMITMENT_UPDATED", "COMMITMENT", c.getId(),
                "Commitment updated: " + c.getDescription() + " (" + c.getStatus() + ")");
        return respond(c, eventId);
    }

    /**
     * Turns a quote into a commitment. Refused unless the budget is approved;
     * refused if it would take the line over its planned amount unless an
     * override reason is given.
     */
    @Transactional
    public CommitmentResponse approveCommitment(TenantId tenantId, UUID eventId, UUID commitmentId,
                                                String overrideReason) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        EventCommitment c = findCommitment(eventId, commitmentId);

        EventBudget budget = budgetRepo.findByEvent(eventId).orElse(null);
        if (budget == null || !budget.isApproved()) {
            throw new HandyFlowException(
                    "The event budget must be approved before supplier commitments can be approved",
                    HttpStatus.CONFLICT, "BUDGET_NOT_APPROVED");
        }

        EventBudgetLine line = findLine(eventId, c.getBudgetLineId());
        BigDecimal currentForecast = BudgetCalculator.lineForecast(line.getId(),
                commitmentRepo.findByEvent(eventId), entryRepo.findActiveByEvent(eventId));
        BigDecimal projected = currentForecast.add(c.getAmount());

        String reason = (overrideReason != null && !overrideReason.isBlank()) ? overrideReason.trim() : null;
        boolean exceeds = projected.compareTo(line.getPlannedAmount()) > 0;
        if (exceeds && reason == null) {
            throw new HandyFlowException(
                    "Approving this would take " + line.getDescription() + " to " + money(projected)
                            + " against a planned " + money(line.getPlannedAmount())
                            + " — approve again with an override reason to proceed",
                    HttpStatus.CONFLICT, "BUDGET_EXCEEDED");
        }

        c.approve(currentUserId(), TenantContext.getCurrentUserName(), exceeds ? reason : null);
        commitmentRepo.save(c);
        audit.record(tenantId, eventId, "COMMITMENT_APPROVED", "COMMITMENT", c.getId(),
                "Commitment approved: " + c.getDescription() + " " + money(c.getAmount())
                        + (exceeds ? " — OVER BUDGET, override: " + reason : ""));
        return respond(c, eventId);
    }

    @Transactional
    public CommitmentResponse completeCommitment(TenantId tenantId, UUID eventId, UUID commitmentId) {
        lockEvent(tenantId, eventId);
        EventCommitment c = findCommitment(eventId, commitmentId);
        c.complete();
        commitmentRepo.save(c);
        audit.record(tenantId, eventId, "COMMITMENT_COMPLETED", "COMMITMENT", c.getId(),
                "Commitment completed: " + c.getDescription());
        return respond(c, eventId);
    }

    /**
     * Cancelling is blocked while active invoices exist against the commitment —
     * void them first (with a reason) so the cost position stays honest. Payments
     * already made (a deposit) do not block it; they are flagged for recovery.
     */
    @Transactional
    public CommitmentResponse cancelCommitment(TenantId tenantId, UUID eventId, UUID commitmentId, String reason) {
        lockEvent(tenantId, eventId);
        EventCommitment c = findCommitment(eventId, commitmentId);
        if (entryRepo.existsActiveInvoiceForCommitment(commitmentId)) {
            throw new HandyFlowException(
                    "This commitment has invoices recorded against it — void them first, then cancel",
                    HttpStatus.CONFLICT, "COMMITMENT_HAS_INVOICES");
        }
        c.cancel(reason.trim());
        commitmentRepo.save(c);
        audit.record(tenantId, eventId, "COMMITMENT_CANCELLED", "COMMITMENT", c.getId(),
                "Commitment cancelled: " + c.getDescription() + " — " + reason.trim());
        return respond(c, eventId);
    }

    // ── Cost entries ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CostEntryResponse> listEntries(TenantId tenantId, UUID eventId, UUID commitmentId, UUID lineId,
                                               String kind, String status, Pageable pageable) {
        findEvent(tenantId, eventId);
        return entryRepo.search(eventId, commitmentId, lineId, upperOrNull(kind), upperOrNull(status), pageable)
                .map(this::toEntryResponse);
    }

    /**
     * Records an invoice received or a payment made. Allowed in any event
     * status — close-out and cancellation costs still need recording.
     */
    @Transactional
    public CostEntryResponse recordEntry(TenantId tenantId, UUID eventId, CostEntryRequest req) {
        lockEvent(tenantId, eventId);

        EventCommitment commitment = null;
        UUID lineId;
        if (req.commitmentId() != null) {
            commitment = findCommitment(eventId, req.commitmentId());
            if (!commitment.isCounted()) {
                throw new HandyFlowException(
                        "Costs can only be recorded against an APPROVED or COMPLETED commitment (this one is "
                                + commitment.getStatus() + ")",
                        HttpStatus.CONFLICT, "COMMITMENT_NOT_APPROVED");
            }
            if (req.budgetLineId() != null && !req.budgetLineId().equals(commitment.getBudgetLineId())) {
                throw new HandyFlowException("The budget line doesn't match the commitment's budget line",
                        HttpStatus.BAD_REQUEST, "LINE_MISMATCH");
            }
            lineId = commitment.getBudgetLineId();
        } else {
            if (req.budgetLineId() == null) {
                throw new HandyFlowException("Give a commitmentId, or a budgetLineId for a cost with no commitment",
                        HttpStatus.BAD_REQUEST, "LINE_REQUIRED");
            }
            requireLine(eventId, req.budgetLineId());
            lineId = req.budgetLineId();
        }
        if (req.deposit() && commitment == null) {
            throw new HandyFlowException("A deposit must be recorded against a commitment",
                    HttpStatus.BAD_REQUEST, "DEPOSIT_NEEDS_COMMITMENT");
        }

        BigDecimal amount = req.amount().setScale(2, RoundingMode.HALF_UP);
        String reference = blankToNull(req.reference());
        String sourceModule = blankToNull(req.sourceModule());
        String sourceRef = blankToNull(req.sourceRef());

        // Payments may not run ahead of what the commitment justifies.
        if (commitment != null && EventCostEntry.PAYMENT.equals(req.kind())) {
            List<EventCostEntry> active = entryRepo.findActiveByEvent(eventId);
            BigDecimal ceiling = commitment.getAmount().max(BudgetCalculator.invoicedAgainst(commitment.getId(), active));
            BigDecimal paidAfter = BudgetCalculator.paidAgainst(commitment.getId(), active).add(amount);
            if (paidAfter.compareTo(ceiling) > 0) {
                throw new HandyFlowException(
                        "This payment would take total payments to " + money(paidAfter)
                                + ", above the " + money(ceiling) + " committed/invoiced for this supplier",
                        HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_COMMITMENT");
            }
        }

        if (sourceRef != null && entryRepo.existsActiveBySource(eventId, sourceModule, sourceRef)) {
            throw new HandyFlowException("That accounting record is already linked to a cost entry for this event",
                    HttpStatus.CONFLICT, "DUPLICATE_SOURCE");
        }
        if (reference != null && entryRepo.existsActiveDuplicate(eventId, req.kind(), amount, reference)) {
            throw new HandyFlowException(
                    "An active " + req.kind().toLowerCase(Locale.ENGLISH) + " for " + money(amount)
                            + " with reference '" + reference + "' already exists — if it's a different one, change the reference",
                    HttpStatus.CONFLICT, "DUPLICATE_ENTRY");
        }

        EventCostEntry entry = EventCostEntry.create(tenantId, eventId, lineId,
                commitment != null ? commitment.getId() : null, req.kind(), req.deposit(), amount,
                req.entryDate(), reference, req.description(), sourceModule, sourceRef,
                currentUserId(), TenantContext.getCurrentUserName());
        entryRepo.save(entry);

        audit.record(tenantId, eventId, "COST_RECORDED", "COST_ENTRY", entry.getId(),
                (entry.isDeposit() ? "Deposit" : capitalise(entry.getKind())) + " recorded: " + money(amount)
                        + (reference != null ? " (ref " + reference + ")" : "")
                        + (entry.isReconciled() ? " — linked to " + sourceModule : " — not linked to an accounting record"));
        return toEntryResponse(entry);
    }

    /** Entries are never deleted. A mistake is voided with a reason and drops out of every total. */
    @Transactional
    public CostEntryResponse voidEntry(TenantId tenantId, UUID eventId, UUID entryId, String reason) {
        lockEvent(tenantId, eventId);
        EventCostEntry entry = entryRepo.findByIdAndEvent(entryId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Cost entry", entryId.toString()));
        entry.voidEntry(reason.trim(), TenantContext.getCurrentUserName());
        entryRepo.save(entry);
        audit.record(tenantId, eventId, "COST_VOIDED", "COST_ENTRY", entry.getId(),
                capitalise(entry.getKind()) + " of " + money(entry.getAmount()) + " voided — " + reason.trim());
        return toEntryResponse(entry);
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

    private void assertPlanningOpen(Event event) {
        if ("COMPLETED".equals(event.getStatus()) || "CANCELLED".equals(event.getStatus())) {
            throw new HandyFlowException(
                    "Supplier planning is closed for a " + event.getStatus() + " event — only cost entries can still be recorded",
                    HttpStatus.CONFLICT, "EVENT_CLOSED");
        }
    }

    private EventCommitment findCommitment(UUID eventId, UUID commitmentId) {
        return commitmentRepo.findByIdAndEvent(commitmentId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Commitment", commitmentId.toString()));
    }

    private EventBudgetLine findLine(UUID eventId, UUID lineId) {
        return lineRepo.findByIdAndEvent(lineId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget line", lineId.toString()));
    }

    private void requireLine(UUID eventId, UUID lineId) {
        findLine(eventId, lineId);
    }

    private void requireVendor(UUID eventId, UUID vendorId) {
        if (vendorId != null) {
            vendorRepo.findByIdAndEvent(vendorId, eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId.toString()));
        }
    }

    private Map<UUID, EventBudgetLine> lineMap(UUID eventId) {
        Map<UUID, EventBudgetLine> map = new HashMap<>();
        for (EventBudgetLine l : lineRepo.findByEvent(eventId)) map.put(l.getId(), l);
        return map;
    }

    private Map<UUID, String> vendorNames(UUID eventId) {
        return vendorRepo.findByEvent(eventId).stream()
                .collect(Collectors.toMap(EventVendor::getId, EventVendor::getCompanyName, (a, b) -> a));
    }

    private CommitmentResponse respond(EventCommitment c, UUID eventId) {
        return toResponse(c, lineMap(eventId), vendorNames(eventId), entryRepo.findActiveByEvent(eventId));
    }

    private CommitmentResponse toResponse(EventCommitment c, Map<UUID, EventBudgetLine> lines,
                                          Map<UUID, String> vendors, List<EventCostEntry> activeEntries) {
        EventBudgetLine line = lines.get(c.getBudgetLineId());
        return new CommitmentResponse(c.getId(), c.getBudgetLineId(),
                line != null ? line.getCategory() : null,
                c.getVendorId(), c.getVendorId() != null ? vendors.get(c.getVendorId()) : null,
                c.getDescription(), c.getAmount(), c.getStatus(), c.getQuoteReference(),
                c.getPoReference(), c.getContractReference(), c.getDeliverable(), c.getDueDate(),
                c.getDepositAmount(), c.getDepositDueDate(), c.getApprovedByName(), c.getApprovedAt(),
                c.getOverBudgetReason(), c.getCancelledReason(),
                BudgetCalculator.invoicedAgainst(c.getId(), activeEntries),
                BudgetCalculator.paidAgainst(c.getId(), activeEntries),
                c.getCreatedAt());
    }

    private CostEntryResponse toEntryResponse(EventCostEntry e) {
        return new CostEntryResponse(e.getId(), e.getBudgetLineId(), e.getCommitmentId(), e.getKind(),
                e.isDeposit(), e.getAmount(), e.getEntryDate(), e.getReference(), e.getDescription(),
                e.getSourceModule(), e.getSourceRef(), e.isReconciled(), e.getStatus(), e.getVoidReason(),
                e.getRecordedByName(), e.getCreatedAt());
    }

    private UUID currentUserId() {
        return TenantContext.getCurrentUserId();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String upperOrNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim().toUpperCase(Locale.ENGLISH);
    }

    private static String capitalise(String s) {
        return s.substring(0, 1) + s.substring(1).toLowerCase(Locale.ENGLISH);
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "R%,.2f", amount);
    }
}