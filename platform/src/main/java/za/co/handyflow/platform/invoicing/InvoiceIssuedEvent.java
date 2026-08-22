package za.co.handyflow.platform.invoicing;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FIX: backlog 1.6 — Invoicing had no AccountingFacade dependency at
 * all; invoice issuance never reached the general ledger. Published
 * here rather than InvoiceService calling AccountingFacade directly
 * because accounting already depends on invoicing (AccountingService
 * injects InvoicingFacade) — a direct call the other way would create a
 * circular module dependency. Same DomainEvent-at-module-root pattern
 * as hr.EmployeeCreatedEvent; consumed by a new listener living inside
 * accounting (which can safely import this, since that dependency
 * direction already exists), see
 * accounting.application.internal.InvoicingAccountingEventHandler.
 * <p>
 * Carries the VAT-exclusive subtotal and VAT total separately (not just
 * the combined total) because the ledger entry needs to split them onto
 * different lines — Debit AR (full total), Credit Revenue (subtotal),
 * Credit VAT Output (vatTotal) — standard SA AR practice, matching
 * exactly how AP's own postApprovalJournal() already splits VAT onto
 * its own line rather than burying it inside the expense line.
 */
public record InvoiceIssuedEvent(
        TenantId tenantId,
        UUID invoiceId,
        String invoiceNumber,
        UUID customerId,
        BigDecimal subtotal,
        BigDecimal vatTotal,
        BigDecimal total,
        Instant occurredOn
) implements DomainEvent {

    public static InvoiceIssuedEvent of(TenantId tenantId, UUID invoiceId, String invoiceNumber,
                                        UUID customerId, BigDecimal subtotal, BigDecimal vatTotal, BigDecimal total) {
        return new InvoiceIssuedEvent(tenantId, invoiceId, invoiceNumber, customerId,
                subtotal, vatTotal, total, Instant.now());
    }
}