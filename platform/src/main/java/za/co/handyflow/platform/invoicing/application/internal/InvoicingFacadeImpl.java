package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceStatus;
import za.co.handyflow.platform.invoicing.domain.repository.CreditNoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thin implementation — delegates entirely to InvoiceRepository's existing
 * findAllForVat()/findOutstandingForTenant() queries (unchanged, same SQL,
 * same status/date filtering) and just maps the results into the facade's
 * DTOs. No new query logic, no new business rules — see InvoicingFacade's
 * own Javadoc for why each method is shaped the way it is.
 */
@Service
@RequiredArgsConstructor
class InvoicingFacadeImpl implements InvoicingFacade {

    private final InvoiceRepository invoiceRepo;
    private final CreditNoteRepository creditNoteRepo;
    private final CrmFacade crmFacade;

    @Override
    public VatSummary getVatSummary(TenantId tenantId, LocalDate from, LocalDate to) {
        List<Invoice> invoices = invoiceRepo.findAllForVat(tenantId.getValue().toString(), from, to);

        BigDecimal outputVat = invoices.stream()
                .map(Invoice::getVatTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSubtotal = invoices.stream()
                .map(Invoice::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new VatSummary(invoices.size(), totalSubtotal, outputVat);
    }

    @Override
    public List<OutstandingInvoiceSummary> findOutstandingInvoices(TenantId tenantId) {
        return invoiceRepo.findOutstandingForTenant(tenantId.getValue().toString()).stream()
                .map(inv -> new OutstandingInvoiceSummary(
                        inv.getId(),
                        inv.getInvoiceNumber(),
                        inv.getCustomerId(),
                        inv.getWalkinClientName(),
                        inv.getDueDate(),
                        inv.getTotal(),
                        inv.getAmountPaid()))
                .toList();
    }

    private static final int MAX_INVOICES = 500;

    @Override
    @Transactional(readOnly = true)
    public Optional<SaleLine> findSaleLine(TenantId tenantId, UUID lineItemId) {
        return findSaleLines(tenantId, List.of(lineItemId)).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleLine> findSaleLines(TenantId tenantId, Collection<UUID> lineItemIds) {
        if (lineItemIds == null || lineItemIds.isEmpty()) return List.of();
        Set<UUID> wanted = new HashSet<>(lineItemIds);
        return toSaleLines(tenantId, invoiceRepo.findByLineItemIds(tenantId, wanted), wanted);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleLine> searchSaleLines(TenantId tenantId, Instant from, Instant to, Collection<String> statuses, int maxInvoices) {
        List<InvoiceStatus> parsed = new ArrayList<>();
        if (statuses != null) {
            for (String s : statuses) {
                try { parsed.add(InvoiceStatus.valueOf(s)); } catch (IllegalArgumentException ignored) { /* an unknown status simply matches nothing */ }
            }
        }
        if (parsed.isEmpty()) return List.of();
        int limit = Math.min(Math.max(maxInvoices, 1), MAX_INVOICES);
        return toSaleLines(tenantId, invoiceRepo.findIssuedBetween(tenantId, parsed, from, to, PageRequest.of(0, limit)), null);
    }

    /** @param onlyLines when not null, only these line items are returned; null returns every line of every invoice given */
    private List<SaleLine> toSaleLines(TenantId tenantId, List<Invoice> invoices, Set<UUID> onlyLines) {
        if (invoices.isEmpty()) return List.of();
        Map<UUID, BigDecimal> credited = new HashMap<>();
        for (Object[] r : creditNoteRepo.sumSubtotalByInvoice(tenantId, invoices.stream().map(Invoice::getId).toList())) {
            credited.put((UUID) r[0], r[1] instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(r[1])));
        }
        Map<UUID, String> customerNames = new HashMap<>();          // each customer is looked up once
        List<SaleLine> out = new ArrayList<>();
        for (Invoice inv : invoices) {
            String customerName = inv.getWalkinClientName();
            if (inv.getCustomerId() != null) {
                customerName = customerNames.computeIfAbsent(inv.getCustomerId(),
                        id -> crmFacade.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse(null));
            }
            for (InvoiceLineItem li : inv.getLineItems()) {
                if (onlyLines != null && !onlyLines.contains(li.getId())) continue;
                out.add(new SaleLine(inv.getId(), inv.getInvoiceNumber(), inv.getStatus().name(), inv.getCustomerId(), customerName,
                        inv.getIssuedAt(), inv.getCurrency(), li.getId(), li.getDescription(), li.getUnit(), li.getQuantity(),
                        li.getUnitPrice(), li.getLineTotal(), inv.getSubtotal(), credited.getOrDefault(inv.getId(), BigDecimal.ZERO)));
            }
        }
        return out;
    }
}
