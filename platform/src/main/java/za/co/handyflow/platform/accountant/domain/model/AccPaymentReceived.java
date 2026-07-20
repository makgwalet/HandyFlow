package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A payment received against a fee note.
 * <p>
 * CORRECTION: this table (acc_payments_received) already existed —
 * created in V58__accountant_module.sql, before this payment-recording
 * feature was ever built. It just sat unused; AccountantService.
 * toFeeNoteResponse() hardcoded amountPaid to ZERO instead of querying
 * it. The table's original schema was leaner than this entity first
 * assumed (missing notes/recorded_by/recorded_by_name, and
 * payment_method is nullable, not required) — confirmed by reading the
 * real V58 migration after a duplicate-table startup failure. This
 * entity and its own migration (the ALTER TABLE adding the three
 * missing columns) now match reality exactly.
 * <p>
 * Deliberately a simple append-only ledger, not a mutable "amount paid"
 * field on FeeNote itself — a practice needs to see WHEN each payment
 * came in and by what method (EFT/cash/card/debit order) for
 * reconciliation, not just a final total. FeeNote.applyPayment() derives
 * status from the sum of these rows; it doesn't store the total itself.
 */
@Entity(name = "AccountantPaymentReceived")
@Table(name = "acc_payments_received")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccPaymentReceived {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id",   nullable = false) private UUID tenantId;
    @Column(name = "fee_note_id", nullable = false) private UUID feeNoteId;
    @Column(name = "client_id",   nullable = false) private UUID clientId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    // FIX: confirmed against the real V58 migration — this table
    // already existed before this feature was built, with a leaner
    // schema than originally assumed here. payment_method is genuinely
    // nullable at the DB level (enforcement is at the DTO layer instead
    // — RecordPaymentRequest.paymentMethod() is @NotBlank, so every
    // payment created through this app always sets it; the column
    // itself just isn't constrained). Not tightening this with a
    // migration — the table's history/row count before this feature
    // existed isn't known, and asserting NOT NULL retroactively without
    // that knowledge is a real risk, not a formality.
    @Column(name = "payment_method")
    private String paymentMethod;

    // FIX: real column is VARCHAR(100), not Hibernate's 255 default.
    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by")      private UUID recordedBy;
    @Column(name = "recorded_by_name") private String recordedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AccPaymentReceived create(UUID tenantId, UUID feeNoteId, UUID clientId,
                                            BigDecimal amount, LocalDate paymentDate,
                                            String paymentMethod, String reference, String notes,
                                            UUID recordedBy, String recordedByName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        AccPaymentReceived p = new AccPaymentReceived();
        p.tenantId        = tenantId;
        p.feeNoteId        = feeNoteId;
        p.clientId         = clientId;
        p.amount           = amount;
        p.paymentDate      = paymentDate;
        p.paymentMethod    = paymentMethod;
        p.reference        = reference;
        p.notes            = notes;
        p.recordedBy       = recordedBy;
        p.recordedByName   = recordedByName;
        p.createdAt        = Instant.now();
        return p;
    }
}