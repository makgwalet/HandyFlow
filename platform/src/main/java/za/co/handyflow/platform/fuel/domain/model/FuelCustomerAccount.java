package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fuel-specific commercial layer on top of a CRM customer: classification, credit terms, credit hold. */
@Entity
@Table(name = "fuel_customer_accounts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelCustomerAccount {

    public static final java.util.Set<String> TYPES = java.util.Set.of(
            "MINING_COMPANY", "MINE_CONTRACTOR", "AGRICULTURAL_BUSINESS", "COMMERCIAL_FARM", "CONSTRUCTION_COMPANY",
            "CIVIL_CONTRACTOR", "TRANSPORT_LOGISTICS", "FLEET_OPERATOR", "FUEL_RETAILER", "INDUSTRIAL",
            "GOVERNMENT", "RESELLER", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "crm_customer_id", nullable = false, updatable = false)
    private UUID crmCustomerId;

    @Column(name = "customer_type", nullable = false, length = 30)
    private String customerType = "OTHER";

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 30;

    @Column(name = "credit_hold", nullable = false)
    private boolean creditHold = false;

    @Column(name = "credit_hold_reason", length = 300)
    private String creditHoldReason;

    @Column(name = "licence_number", length = 60)
    private String licenceNumber;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelCustomerAccount create(TenantId tenantId, UUID crmCustomerId, String type, BigDecimal creditLimit,
                                             int termsDays, String licence, String notes) {
        FuelCustomerAccount a = new FuelCustomerAccount();
        a.tenantId = tenantId; a.crmCustomerId = crmCustomerId;
        a.apply(type, creditLimit, termsDays, licence, notes);
        a.createdAt = Instant.now();
        return a;
    }

    public void apply(String type, BigDecimal creditLimit, int termsDays, String licence, String notes) {
        if (type == null || !TYPES.contains(type)) throw new IllegalArgumentException("Unknown customer type: " + type);
        if (creditLimit == null || creditLimit.signum() < 0) throw new IllegalArgumentException("Credit limit cannot be negative.");
        if (termsDays < 0 || termsDays > 365) throw new IllegalArgumentException("Payment terms must be 0 to 365 days.");
        this.customerType = type; this.creditLimit = creditLimit; this.paymentTermsDays = termsDays;
        this.licenceNumber = licence == null || licence.isBlank() ? null : licence.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.updatedAt = Instant.now();
    }

    public void placeOnHold(String reason) {
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters for the credit hold.");
        this.creditHold = true; this.creditHoldReason = reason.trim(); this.updatedAt = Instant.now();
    }

    public void releaseHold() { this.creditHold = false; this.creditHoldReason = null; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.active = false; this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
