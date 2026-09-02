package za.co.handyflow.platform.collectionsagency.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The trust ledger's audit trail — every movement of money that belongs
 * to a creditor client, not the agency itself. This is the FIRST
 * trust-accounting pattern in this codebase (confirmed by search before
 * building) and is deliberately self-contained: it never touches the
 * tenant's real chart of accounts (see package-info and
 * CollAgencyTrustTransactionService for the full reasoning — this was a
 * confirmed design decision, not a guess).
 * <p>
 * Two transaction types:
 * <ul>
 *   <li>RECEIPT — a debtor payment received and held in trust. Always
 *   linked to a specific debtorAccountId. Increases
 *   CollAgencyClient.trustBalance.</li>
 *   <li>REMITTANCE — money paid out to the client (net of commission),
 *   processed per-client across their whole outstanding trust balance,
 *   not per-debtor. debtorAccountId is null on a REMITTANCE row — see
 *   CollAgencyTrustTransactionService.processRemittance(). Decreases
 *   CollAgencyClient.trustBalance.</li>
 * </ul>
 * Append-only, same immutability rationale as every other compliance/
 * financial log in this engagement — a ledger entry is corrected with a
 * new offsetting entry, never edited in place.
 */
@Entity
@Table(name = "collagency_trust_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyTrustTransaction {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "debtor_account_id")
    private UUID debtorAccountId; // null for REMITTANCE — see class Javadoc

    @Column(name = "transaction_type", nullable = false)
    private String transactionType; // RECEIPT | REMITTANCE

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount; // always positive; meaning depends on transactionType

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "reference")
    private String reference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CollAgencyTrustTransaction receipt(UUID tenantId, UUID clientId, UUID debtorAccountId,
                                                      BigDecimal amount, LocalDate transactionDate,
                                                      String reference, String notes, UUID recordedByUserId) {
        if (debtorAccountId == null) {
            throw new IllegalArgumentException("debtorAccountId is required for a RECEIPT");
        }
        return create(tenantId, clientId, debtorAccountId, "RECEIPT", amount, transactionDate, reference, notes,
                recordedByUserId);
    }

    public static CollAgencyTrustTransaction remittance(UUID tenantId, UUID clientId, BigDecimal amount,
                                                         LocalDate transactionDate, String reference, String notes,
                                                         UUID recordedByUserId) {
        return create(tenantId, clientId, null, "REMITTANCE", amount, transactionDate, reference, notes,
                recordedByUserId);
    }

    private static CollAgencyTrustTransaction create(UUID tenantId, UUID clientId, UUID debtorAccountId,
                                                      String transactionType, BigDecimal amount,
                                                      LocalDate transactionDate, String reference, String notes,
                                                      UUID recordedByUserId) {
        if (clientId == null) {
            throw new IllegalArgumentException("clientId is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        CollAgencyTrustTransaction t = new CollAgencyTrustTransaction();
        t.tenantId = tenantId;
        t.clientId = clientId;
        t.debtorAccountId = debtorAccountId;
        t.transactionType = transactionType;
        t.amount = amount;
        t.transactionDate = transactionDate != null ? transactionDate : LocalDate.now();
        t.reference = reference;
        t.notes = notes;
        t.recordedByUserId = recordedByUserId;
        t.createdAt = Instant.now();
        return t;
    }
}
