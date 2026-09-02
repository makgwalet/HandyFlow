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
 */
@Service
@RequiredArgsConstructor
public class DebtCollectionCaseService {

    private final DebtCollectionCaseRepository repository;
    private final DebtCollectionNumberGenerator numberGenerator;
    private final InvoicingFacade invoicingFacade;
    private final CrmFacade crmFacade;

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
        BigDecimal total = sumOutstanding(tenantId, invoiceIds);
        String caseNumber = numberGenerator.nextCaseNumber(tenantId);
        DebtCollectionCase c = DebtCollectionCase.open(tenantId, caseNumber, customerId, debtorName, debtorEmail,
                debtorPhone, total, invoiceIds, openedDate, assignedToUserId, assignedToUserName, notes, createdBy);
        return repository.save(c);
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

    @Transactional
    public DebtCollectionCase refreshOutstanding(TenantId tenantId, UUID id) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.refreshTotalOutstanding(sumOutstanding(tenantId, c.getLinkedInvoiceIds()));
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase linkInvoice(TenantId tenantId, UUID id, UUID invoiceId) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.linkInvoice(invoiceId);
        c.refreshTotalOutstanding(sumOutstanding(tenantId, c.getLinkedInvoiceIds()));
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase unlinkInvoice(TenantId tenantId, UUID id, UUID invoiceId) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.unlinkInvoice(invoiceId);
        c.refreshTotalOutstanding(sumOutstanding(tenantId, c.getLinkedInvoiceIds()));
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase assign(TenantId tenantId, UUID id, UUID userId, String userName) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.assign(userId, userName);
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase scheduleNextAction(TenantId tenantId, UUID id, LocalDate date) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.scheduleNextAction(date);
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase updateNotes(TenantId tenantId, UUID id, String notes) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.updateNotes(notes);
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase advanceStatus(TenantId tenantId, UUID id, CaseStatus newStatus) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.advanceStatus(newStatus);
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase writeOff(TenantId tenantId, UUID id, BigDecimal amount, String reason) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.writeOff(amount, reason);
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase close(TenantId tenantId, UUID id, ClosureReason reason, String outcomeNotes) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.close(reason, outcomeNotes);
        return repository.save(c);
    }

    @Transactional
    public DebtCollectionCase linkContract(TenantId tenantId, UUID id, UUID contractId) {
        DebtCollectionCase c = findActive(tenantId, id);
        c.linkContract(contractId);
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
        repository.save(c);
    }

    DebtCollectionCase findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("DebtCollectionCase", id.toString()));
    }

    /**
     * amountPaid may be null on OutstandingInvoiceSummary — treated as zero,
     * same null-handling InvoicingFacade's own Javadoc documents
     * AccountingService already applies to this exact field.
     */
    private BigDecimal sumOutstanding(TenantId tenantId, Set<UUID> invoiceIds) {
        Map<UUID, OutstandingInvoiceSummary> byId = invoicingFacade.findOutstandingInvoices(tenantId).stream()
                .collect(Collectors.toMap(OutstandingInvoiceSummary::id, Function.identity()));
        BigDecimal total = BigDecimal.ZERO;
        for (UUID invoiceId : invoiceIds) {
            OutstandingInvoiceSummary inv = byId.get(invoiceId);
            if (inv == null) {
                throw new IllegalArgumentException("Invoice " + invoiceId + " is not a current outstanding invoice for this tenant");
            }
            BigDecimal paid = inv.amountPaid() != null ? inv.amountPaid() : BigDecimal.ZERO;
            total = total.add(inv.total().subtract(paid));
        }
        return total;
    }
}
