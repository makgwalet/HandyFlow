// invoicing/application/internal/InvoiceQueryService.java
package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.dto.InvoiceResponse;
import za.co.handyflow.platform.invoicing.dto.LineItemResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> getInvoices(TenantId tenantId, Pageable pageable) {
        return invoiceRepository.findAllActive(tenantId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(TenantId tenantId, UUID id) {
        return invoiceRepository
                .findActiveByIdWithLineItems(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));
    }

    private InvoiceResponse toResponse(Invoice inv) {
        List<LineItemResponse> lineItems = inv.getLineItems().stream()
                .map(li -> new LineItemResponse(
                        li.getId(), li.getCatalogueItemId(),
                        li.getDescription(), li.getUnit(),
                        li.getQuantity(), li.getUnitPrice(),
                        li.getVatRate(), li.getLineTotal(), li.getVatAmount()
                )).toList();

        return new InvoiceResponse(
                // ── core ──────────────────────────────────────────────────
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getStatus().name(),
                inv.getCustomerId(),
                inv.getQuoteId(),
                inv.getTitle(),
                inv.getSubtotal(),
                inv.getVatTotal(),
                inv.getTotal(),
                inv.getAmountPaid(),
                inv.getCurrency(),
                inv.getDueDate(),
                inv.getIssuedAt(),
                lineItems,
                inv.getCreatedAt(),
                // ── type / recurring ──────────────────────────────────────
                // WHY null-safe: existing invoices written before the migration
                // will have invoiceType = null until Flyway sets DEFAULT 'STANDARD'.
                // getInvoiceType() returns the enum; name() gives the String.
                inv.getInvoiceType() != null ? inv.getInvoiceType().name() : "STANDARD",
                inv.getRecurringScheduleId(),
                // ── retainer / upfront-hours ──────────────────────────────
                inv.getCommittedHours(),
                inv.getRatePerHour(),
                // hoursConsumed has a DB default of 0 so it is never null,
                // but guard anyway for in-memory objects created before save.
                inv.getHoursConsumed() != null ? inv.getHoursConsumed() : BigDecimal.ZERO,
                inv.getCreditAmount(),
                // ── walk-in ───────────────────────────────────────────────
                inv.getWalkinClientName(),
                inv.getWalkinClientEmail(),
                inv.getWalkinClientPhone()
        );
    }
}
