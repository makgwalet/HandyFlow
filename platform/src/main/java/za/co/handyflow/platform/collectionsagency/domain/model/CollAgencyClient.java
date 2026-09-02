package za.co.handyflow.platform.collectionsagency.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A creditor client whose debtor portfolio this agency collects on
 * behalf of — the agency's own record of that business, not necessarily
 * a HandyFlow tenant itself. Mirrors RecAgencyClient's role exactly.
 * <p>
 * trustBalance is the materialized running total currently held in trust
 * for this client (money collected from their debtors, not yet
 * remitted) — updated only by CollAgencyTrustTransactionService via
 * increaseTrustBalance()/decreaseTrustBalance(), never set directly.
 * This is a per-client balance, not a single agency-wide trust pool —
 * each client's money is tracked separately even though, in the real
 * world, it likely sits in one physical trust bank account; this
 * module's job is to know how much of that account belongs to which
 * client, which is exactly what a trust reconciliation needs.
 * <p>
 * commissionRatePct is a per-client OVERRIDE of
 * CollAgencyProfile.defaultCommissionPct — null means "use the agency
 * default," same override pattern RecAgencyClient.placementFeePct
 * already established.
 */
@Entity
@Table(name = "collagency_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the AGENCY's tenant

    @Column(name = "trading_name", nullable = false)
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "commission_rate_pct", precision = 5, scale = 2)
    private BigDecimal commissionRatePct; // null = use agency default

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "trust_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal trustBalance = BigDecimal.ZERO;

    @Column(name = "onboarded_at")
    private LocalDate onboardedAt;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static CollAgencyClient create(UUID tenantId, String tradingName, String registrationNumber,
                                           BigDecimal commissionRatePct, String contactName, String contactEmail,
                                           String contactPhone, String address) {
        CollAgencyClient c = new CollAgencyClient();
        c.tenantId = tenantId;
        c.tradingName = tradingName.trim();
        c.registrationNumber = registrationNumber;
        c.commissionRatePct = commissionRatePct;
        c.contactName = contactName;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.address = address;
        c.trustBalance = BigDecimal.ZERO;
        c.onboardedAt = LocalDate.now();
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String tradingName, String registrationNumber, BigDecimal commissionRatePct,
                        String contactName, String contactEmail, String contactPhone, String address,
                        String notes) {
        this.tradingName = tradingName.trim();
        this.registrationNumber = registrationNumber;
        this.commissionRatePct = commissionRatePct;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = "INACTIVE";
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    /** Called only by the trust-transaction service when a debtor payment is recorded. */
    public void increaseTrustBalance(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.trustBalance = this.trustBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    /** Called only by the trust-transaction service when a remittance is processed. Rejects overdraw — cannot remit more than is actually held in trust for this client. */
    public void decreaseTrustBalance(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount.compareTo(this.trustBalance) > 0) {
            throw new IllegalStateException(
                    "Cannot remit " + amount + " — only " + this.trustBalance + " is held in trust for this client");
        }
        this.trustBalance = this.trustBalance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
