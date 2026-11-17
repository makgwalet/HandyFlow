package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.MatchExceptionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One finding from a match run. Re-running a match marks the previous OPEN rows
 * SUPERSEDED (history kept) and inserts fresh OPEN rows — so the table doubles as
 * the audit trail of why an invoice was held and what changed.
 */
@Entity
@Table(name = "sc_match_exceptions")
@Getter
@NoArgsConstructor
public class ScMatchException {

    public static final String OPEN = "OPEN";
    public static final String SUPERSEDED = "SUPERSEDED";
    public static final String RESOLVED = "RESOLVED";

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "supplier_invoice_id", nullable = false) UUID supplierInvoiceId;
    @Column(name = "po_line_id") UUID poLineId;
    @Column(name = "invoice_line_id") UUID invoiceLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 40)
    MatchExceptionType exceptionType;

    @Column(nullable = false, length = 1000) String message;
    @Column(name = "expected_value", precision = 18, scale = 4) BigDecimal expectedValue;
    @Column(name = "actual_value", precision = 18, scale = 4) BigDecimal actualValue;
    @Column(nullable = false, length = 12) String status = OPEN;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "resolved_at") Instant resolvedAt;

    public static ScMatchException raise(UUID tenantId, UUID invoiceId, MatchExceptionType type,
                                         UUID poLineId, UUID invoiceLineId, String message,
                                         BigDecimal expected, BigDecimal actual) {
        ScMatchException e = new ScMatchException();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.supplierInvoiceId = invoiceId;
        e.exceptionType = type;
        e.poLineId = poLineId;
        e.invoiceLineId = invoiceLineId;
        e.message = message != null && message.length() > 1000 ? message.substring(0, 1000) : message;
        e.expectedValue = expected;
        e.actualValue = actual;
        e.status = OPEN;
        e.createdAt = Instant.now();
        return e;
    }

    public void supersede() {
        if (OPEN.equals(status)) { status = SUPERSEDED; resolvedAt = Instant.now(); }
    }

    public void resolve() {
        if (OPEN.equals(status)) { status = RESOLVED; resolvedAt = Instant.now(); }
    }
}
