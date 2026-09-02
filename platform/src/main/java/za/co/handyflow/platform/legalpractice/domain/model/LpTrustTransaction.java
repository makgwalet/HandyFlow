package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One movement of a client's trust money — the compliance-critical
 * centerpiece of this module, adapted from {@code CollAgencyTrustTransaction}
 * (the confirmed first trust-accounting pattern in this codebase) but with
 * a Legal-Practice-specific type taxonomy, because a law firm's real trust
 * flow differs materially from a collections agency's: a firm routinely
 * pays its OWN earned fees, and third-party disbursements, directly out of
 * a client's trust deposit — flows CollAgencyTrustTransaction's own
 * RECEIPT/REMITTANCE pair was never built to represent.
 * <p>
 * Four types, each with its own factory-enforced required/forbidden field
 * combination — mirroring CollAgencyTrustTransaction's own
 * belt-and-braces approach (entity-level validation here, a matching DB
 * CHECK constraint at the migration layer):
 * <ul>
 *   <li><b>RECEIPT</b> — money deposited into trust by/for the client.
 *       Forbids {@code invoiceId} and {@code payee} (nothing has been
 *       earned or paid out yet).</li>
 *   <li><b>TRANSFER_TO_BUSINESS</b> — the firm draws its own earned fees
 *       out of trust. REQUIRES {@code invoiceId} — the Legal Practice Act
 *       compliance control confirmed via AskUserQuestion: this type must
 *       never represent an unattached withdrawal, only settlement of a
 *       real, issued invoice.</li>
 *   <li><b>DISBURSEMENT_PAYMENT</b> — trust pays a third party directly on
 *       the client's behalf (Sheriff, correspondent attorney, counsel).
 *       REQUIRES {@code payee}; forbids {@code invoiceId} (this is not the
 *       firm's own revenue). See the module's own scope-decision note —
 *       this type is a reasoned extension of the confirmed
 *       TRANSFER_TO_BUSINESS capability, not independently confirmed.</li>
 *   <li><b>REFUND</b> — trust money returned directly to the client.
 *       REQUIRES {@code payee} (the client, for the paper trail); forbids
 *       {@code invoiceId}.</li>
 * </ul>
 * Every transaction adjusts {@code LpClient.trustBalance} via its
 * overdraw-guarded increase/decrease pair in the same service-layer
 * transaction that creates this row — this entity itself does not reach
 * into LpClient, matching CollAgencyTrustTransactionService's own split.
 */
@Entity
@Table(name = "lp_trust_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpTrustTransaction {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "matter_id")
    private UUID matterId; // nullable — a client-level trust receipt may not yet be tied to a matter

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType; // RECEIPT | TRANSFER_TO_BUSINESS | DISBURSEMENT_PAYMENT | REFUND

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "invoice_id")
    private UUID invoiceId; // required for TRANSFER_TO_BUSINESS, forbidden otherwise

    private String payee; // required for DISBURSEMENT_PAYMENT/REFUND, forbidden otherwise

    private String reference;

    @Column(name = "captured_by")
    private UUID capturedBy;

    @Column(name = "captured_by_name")
    private String capturedByName;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static LpTrustTransaction create(TenantId tenantId, UUID clientId, UUID matterId,
                                             String transactionType, BigDecimal amount, LocalDate transactionDate,
                                             UUID invoiceId, String payee, String reference,
                                             UUID capturedBy, String capturedByName, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Trust transaction amount must be positive");
        }
        validateTypeFieldCombination(transactionType, invoiceId, payee);

        LpTrustTransaction t = new LpTrustTransaction();
        t.tenantId = tenantId;
        t.clientId = clientId;
        t.matterId = matterId;
        t.transactionType = transactionType;
        t.amount = amount;
        t.transactionDate = transactionDate != null ? transactionDate : LocalDate.now();
        t.invoiceId = invoiceId;
        t.payee = payee;
        t.reference = reference;
        t.capturedBy = capturedBy;
        t.capturedByName = capturedByName;
        t.notes = notes;
        t.createdAt = Instant.now();
        return t;
    }

    private static void validateTypeFieldCombination(String type, UUID invoiceId, String payee) {
        switch (type) {
            case "RECEIPT" -> {
                if (invoiceId != null) {
                    throw new IllegalArgumentException("RECEIPT must not carry an invoiceId");
                }
                if (payee != null) {
                    throw new IllegalArgumentException("RECEIPT must not carry a payee");
                }
            }
            case "TRANSFER_TO_BUSINESS" -> {
                if (invoiceId == null) {
                    throw new IllegalArgumentException("TRANSFER_TO_BUSINESS requires an invoiceId");
                }
                if (payee != null) {
                    throw new IllegalArgumentException("TRANSFER_TO_BUSINESS must not carry a payee");
                }
            }
            case "DISBURSEMENT_PAYMENT" -> {
                if (payee == null || payee.isBlank()) {
                    throw new IllegalArgumentException("DISBURSEMENT_PAYMENT requires a payee");
                }
                if (invoiceId != null) {
                    throw new IllegalArgumentException("DISBURSEMENT_PAYMENT must not carry an invoiceId");
                }
            }
            case "REFUND" -> {
                if (payee == null || payee.isBlank()) {
                    throw new IllegalArgumentException("REFUND requires a payee");
                }
                if (invoiceId != null) {
                    throw new IllegalArgumentException("REFUND must not carry an invoiceId");
                }
            }
            default -> throw new IllegalArgumentException("Unknown trust transaction type: " + type);
        }
    }

    /** RECEIPT increases trust balance; every other type decreases it. */
    public boolean increasesTrustBalance() {
        return "RECEIPT".equals(this.transactionType);
    }
}
