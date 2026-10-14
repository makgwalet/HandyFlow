package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.ClosureReason;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.OutstandingInvoiceSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Core service for DebtCollectionCase. totalOutstanding is computed by
 * summing (total - amountPaid) across the linked invoices as reported by
 * InvoicingFacade.findOutstandingInvoices() at the moment of open()/
 * refreshOutstanding() — this module does not add a new facade method for
 * "look up these specific invoice ids," it filters the existing
 * tenant-wide outstanding list client-side. That list is expected to stay
 * small enough per tenant for this to be fine; if it isn't, the fix is a
 * new InvoicingFacade method, not a workaround here.
 * <p>
 * PAID INVOICES (fix). An invoice that has left invoicing's outstanding list has been paid. That is only an
 * error when a case is being OPENED or an invoice is being newly LINKED (you cannot collect on something not
 * owed): those paths are strict. Every other path — refreshOutstanding(), unlinkInvoice(), and the re-total
 * after linkInvoice() — treats an already-linked invoice that is no longer outstanding as paid and counts it as
 * zero. Previously these paths threw as soon as ONE linked invoice was paid, which made it impossible to refresh
 * a partly paid case (exactly when refreshing matters most). A case whose invoices are all paid refreshes to 0;
 * closing it stays a deliberate staff action.
 * <p>
 * AUDIT. Every mutating operation (open, balance refresh, invoice link/unlink, assignment, next action, notes,
 * status change, write-off, close, contract link, delete) writes an append-only CaseAuditEvent in the same
 * transaction via CaseAuditService, recording who did it (from the request's TenantContext, or "system") and the
 * before → after values. recordContact() is not audited here: the contact log is itself the compliance trail.
 */
@Service
@RequiredArgsConstructor
public class DebtCollectionCaseService {

    private final DebtCollectionCaseRepository repository;
    private final DebtCollectionNumberGenerator numberGenerator;
    private final InvoicingFacade invoicingFacade;
    private final CrmFacade crmFacade;
    private final CaseAuditService audit;

    @Transactional
    public DebtCollectionCase open(TenantId tenantId, UUID customerId, String debtorName, String debtorEmail,
                                    String debtorPhone, Set<UUID> invoiceIds, LocalDate openedDate,
                                    UUID assignedToUserId, String assignedToUserName, String notes, UUID createdBy) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            throw new IllegalArgumentException("At least one outstanding invoice must be linked when opening a case");
        }
        if (customerId != null && !repository.findOpenByCustomerId(tenantId, customerId).isEmpty()) {
            throw new IllegalStateException(
                    "An open debt collection case already exists for this customer — link the new invoice(s) to "
                            + "that case (see linkInvoice()) instead of opening a duplicate case for the same debtor");
        }
        BigDecimal total = sumOutstanding(outstandingByInvoice(tenantId), invoiceIds, true);
        String caseNumber = numberGenerator.nextCaseNumber(tenantId);
        DebtCollectionCase c = DebtCollectionCase.open(tenantId, caseNumber, customerId, debtorName, debtorEmail,
                debtorPhone, total, invoiceIds, openedDate, assignedToUserId, assignedToUserName, notes, createdBy);
        DebtCollectionCase saved = repository.save(c);
        audit.record(tenantId, saved.getId(), "CASE_OPENED", caseNumber + " opened for " + debtorName + ": R" + total
                + " across " + invoiceIds.size() + " invoice(s)"
                + (assignedToUserName != null ? ", assigned to " + assignedToUserName : ""));
        return saved;
    }

    /**
     * Convenience path for the common case: staff picked a CRM customer
     * rather than typing debtor details by hand. Pulls the customer's
     * contact snapshot from CrmFacade and their full outstanding-invoice
     * set from InvoicingFacade, so the UI only needs to pass a customerId.
     */
    @Transactional
    public DebtCollectionCase openForCustomer(TenantId tenantId, UUID customerId, LocalDate openedDate,
                                               UUID assignedToUserId, String assignedToUserName, String notes,
                                               UUID createdBy) {
        CustomerSummary customer = crmFacade.findCustomerById(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId.toString()));
        Set<UUID> invoiceIds = invoicingFacade.findOutstandingInvoices(tenantId).stream()
                .filter(inv -> customerId.equals(inv.customerId()))
                .map(OutstandingInvoiceSummary::id)
                .collect(Collectors.toSet());
        return open(tenantId, customerId, customer.name(), customer.email(), customer.phone(), invoiceIds,
                openedDate, assignedToUserId, assignedToUserName, notes, createdBy);
    }

    /** Outstanding invoices for a customer, for the UI to present as a picker before opening a case. Not persisted — pure passthrough to InvoicingFacade, filtered client-side (see class Javadoc). */
    @Transactional(readOnly = true)
    public List<OutstandingInvoiceSummary> findOutstandingInvoicesForCustomer(TenantId tenantId, UUID customerId) {
        return invoicingFacade.findOutstandingInvoices(tenantId).stream()
                .filter(inv -> customerId.equals(inv.customerId()))
                .toList();
    }

    /** Tolerant: invoices already paid in full count as zero (see class Javadoc). */
    @Transactional
    public DebtCollectionCase refreshOutstanding(TenantId tenantId, UUID id) {
        DebtCollectionCase c = findActive(tenantId, id);
        BigDecimal before = c.getTotalOutstanding();
        c.refreshTotalOutstanding(sumOutstanding(outstandingByInvoice(tenantId), c.getLinkedInvoiceIds(), false));
        if (before.compareTo(c.getTotalOutstanding()) != 0) {
            audit.record(tenantId, id, "BALANCE_REFRESHED", "R" + before + " → R" + c.getTotalOutstanding());
        }
        return repository.save(c);
    }

    /** The NEW invoice must currently be outstanding (strict); the re-total then tolerates earlier-linked invoices that have since been paid. */
    @Transactional
    public DebtCollectionCase linkInvoice(TenantId tenantId, UUID id, UUID invoiceId) {
        DebtCollectionCase c = findActive(tenantId, id);
        Map<UUID, BigDecimal> outstanding = outstandingByInvoice(tenantId);
        sumOutstanding(outstanding, Set.of(invoiceId), true);
        BigDecimal before = c.getTotalOutstanding();
        c.linkInvoice(invoiceId);
        c.refreshTotalOutstanding(sumOutstanding(outstanding, c.getLinkedInvoiceIds(), false));
        audit.record(tenantId, id, "INVOICE_LINKED", "invoice " + invoiceId + "; balance R" + before + " → R" + c.getTotalOutstanding());
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase unlinkInvoice(TenantId tenantId, UUID id, UUID invoiceId) {
        DebtCollectionCase c = findActive(tenantId, id);
        BigDecimal before = c.getTotalOutstanding();
        c.unlinkInvoice(invoiceId);
        c.refreshTotalOutstanding(sumOutstanding(outstandingByInvoice(tenantId), c.getLinkedInvoiceIds(), false));
        audit.record(tenantId, id, "INVOICE_UNLINKED", "invoice " + invoiceId + "; balance R" + before + " → R" + c.getTotalOutstanding());
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase assign(TenantId tenantId, UUID id, UUID userId, String userName) {
        DebtCollectionCase c = findActive(tenantId, id);
        String from = c.getAssignedToUserName();
        c.assign(userId, userName);
        audit.record(tenantId, id, "ASSIGNED", (from != null ? from : "unassigned") + " → " + (userName != null ? userName : userId));
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase scheduleNextAction(TenantId tenantId, UUID id, LocalDate date) {
        DebtCollectionCase c = findActive(tenantId, id);
        LocalDate before = c.getNextActionDate();
        c.scheduleNextAction(date);
        audit.record(tenantId, id, "NEXT_ACTION_SCHEDULED", (before != null ? before : "none") + " → " + date);
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase updateNotes(TenantId tenantId, UUID id, String notes) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.updateNotes(notes);
        audit.record(tenantId, id, "NOTES_UPDATED", "notes edited (" + (notes == null ? 0 : notes.length()) + " characters)");
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase advanceStatus(TenantId tenantId, UUID id, CaseStatus newStatus) {
        DebtCollectionCase c = findActive(tenantId, id);
        CaseStatus before = c.getStatus();
        c.advanceStatus(newStatus);
        audit.record(tenantId, id, "STATUS_CHANGED", before + " → " + newStatus);
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase writeOff(TenantId tenantId, UUID id, BigDecimal amount, String reason) {
        DebtCollectionCase c = findActive(tenantId, id);
        CaseStatus before = c.getStatus();
        c.writeOff(amount, reason);
        audit.record(tenantId, id, "WRITTEN_OFF", "R" + amount + " (status was " + before + ")"
                + (reason != null && !reason.isBlank() ? ": " + reason : ""));
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase close(TenantId tenantId, UUID id, ClosureReason reason, String outcomeNotes) {
        DebtCollectionCase c = findActive(tenantId, id);
        CaseStatus before = c.getStatus();
        c.close(reason, outcomeNotes);
        audit.record(tenantId, id, "CASE_CLOSED", "reason " + reason + " (status was " + before + ")"
                + (outcomeNotes != null && !outcomeNotes.isBlank() ? ": " + outcomeNotes : ""));
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase linkContract(TenantId tenantId, UUID id, UUID contractId) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.linkContract(contractId);
        audit.record(tenantId, id, "CONTRACT_LINKED", "contract " + contractId);
        return repository.save(c);
    }

    /** Called by CollectionContactLogService after recording a contact, to keep the case's lastContactDate in sync. Package-visible only — not part of this service's public API for controllers. */
    @Transactional
    void recordContact(TenantId tenantId, UUID id, LocalDate contactDate) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.recordContact(contactDate);
        repository.save(c);
    }

    @Transactional(readOnly = true)
    public Page<DebtCollectionCase> list(TenantId tenantId, CaseStatus status, Pageable pageable) {
        return repository.findAllActive(tenantId, status, pageable);
    }

    /** Unpaginated — used by DebtCollectionPdfService's register export. */
    @Transactional(readOnly = true)
    public List<DebtCollectionCase> listAll(TenantId tenantId) {
        return repository.findAllActive(tenantId, null, Pageable.unpaged(Sort.by("openedDate").descending())).getContent();
    }

    @Transactional(readOnly = true)
    public DebtCollectionCase get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional(readOnly = true)
    public long count(TenantId tenantId) {
        return repository.countByTenant(tenantId);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, UUID deletedBy) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.softDelete(deletedBy);
        audit.record(tenantId, id, "CASE_DELETED", "soft-deleted " + c.getCaseNumber());
        repository.save(c);
    }

    DebtCollectionCase findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("DebtCollectionCase", id.toString()));
    }

    /**
     * Invoice id → amount still owed, for every invoice invoicing currently reports as outstanding.
     * amountPaid may be null on OutstandingInvoiceSummary — treated as zero, same null-handling
     * InvoicingFacade's own Javadoc documents AccountingService already applies to this exact field.
     * Duplicate ids (should not happen) keep the first entry instead of failing the whole request.
     */
    private Map<UUID, BigDecimal> outstandingByInvoice(TenantId tenantId) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        for (OutstandingInvoiceSummary inv : invoicingFacade.findOutstandingInvoices(tenantId)) {
            BigDecimal paid = inv.settled();   // finance patch F8: paid plus credit notes
            out.putIfAbsent(inv.id(), inv.total().subtract(paid));
        }
        return out;
    }

    /**
     * Sums the amount owed on the given invoices. strict=true throws if any id is not currently outstanding
     * (open / link-new-invoice); strict=false counts a missing id as paid (zero). Pure — no I/O — so the rules
     * are unit-tested directly. Package-visible for that test.
     */
    static BigDecimal sumOutstanding(Map<UUID, BigDecimal> outstandingByInvoice, Set<UUID> invoiceIds, boolean strict) {
        BigDecimal total = BigDecimal.ZERO;
        for (UUID invoiceId : invoiceIds) {
            BigDecimal owed = outstandingByInvoice.get(invoiceId);
            if (owed == null) {
                if (strict) {
                    throw new IllegalArgumentException("Invoice " + invoiceId + " is not a current outstanding invoice for this tenant");
                }
                continue;
            }
            total = total.add(owed);
        }
        return total;
    }
}
