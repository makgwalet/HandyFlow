package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A client of the firm. Carries its own {@code trustBalance} with the
 * same overdraw-guarded increase/decrease pair as
 * {@code CollAgencyClient} — the confirmed real precedent for trust-money
 * accounting in this codebase. Unlike CollAgencyClient (a creditor whose
 * trust balance represents money collected ON THEIR BEHALF from third-
 * party debtors), an LpClient's trust balance represents money the
 * CLIENT THEMSELVES deposited with the firm — the direction of money
 * flow differs, but the overdraw-guard invariant (never let the balance
 * go negative — that would mean paying out client money that was never
 * actually deposited) is identical, so the same technical pattern is
 * reused deliberately.
 */
@Entity
@Table(name = "lp_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    private String email;

    private String phone;

    @Column(name = "client_type", nullable = false, length = 20)
    private String clientType; // INDIVIDUAL | ENTITY

    @Column(name = "id_or_registration_number")
    private String idOrRegistrationNumber;

    @Column(name = "trust_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal trustBalance = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status; // ACTIVE | INACTIVE

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpClient create(TenantId tenantId, String name, String email, String phone,
                                   String clientType, String idOrRegistrationNumber, String notes) {
        LpClient c = new LpClient();
        c.tenantId = tenantId;
        c.name = name;
        c.email = email;
        c.phone = phone;
        c.clientType = clientType;
        c.idOrRegistrationNumber = idOrRegistrationNumber;
        c.notes = notes;
        c.trustBalance = BigDecimal.ZERO;
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String name, String email, String phone, String idOrRegistrationNumber, String notes) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.idOrRegistrationNumber = idOrRegistrationNumber;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    /** Money deposited into trust by/for this client. */
    public void increaseTrustBalance(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Trust deposit amount must be positive");
        }
        this.trustBalance = this.trustBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Money leaving trust for this client — a TRANSFER_TO_BUSINESS,
     * DISBURSEMENT_PAYMENT, or REFUND. Overdraw-guarded: a firm can never
     * pay out more of a client's trust money than was actually deposited.
     */
    public void decreaseTrustBalance(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Trust withdrawal amount must be positive");
        }
        if (this.trustBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Trust withdrawal of " + amount + " would overdraw client trust balance of " + this.trustBalance);
        }
        this.trustBalance = this.trustBalance.subtract(amount);
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

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
