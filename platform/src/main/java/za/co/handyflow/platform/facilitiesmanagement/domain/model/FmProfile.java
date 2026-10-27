package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The FM company's own business profile — one row per tenant — now also holding its BILLING SETTINGS
 * (Chunk 7): VAT registration, VAT rate, payment terms, and the ledger account codes invoices post to.
 * <p>
 * VAT rules enforced here: a VAT-registered company must have a VAT number and a VAT ledger account code,
 * and the rate must be between 0 and 100.
 */
@Entity
@Table(name = "fm_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "contact_email")
    private String contactEmail;
    @Column(name = "contact_phone")
    private String contactPhone;

    private String notes;

    @Column(name = "vat_registered", nullable = false)
    private boolean vatRegistered = false;

    @Column(name = "vat_number", length = 30)
    private String vatNumber;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = new BigDecimal("15.00");

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 30;

    @Column(name = "ar_account_code", nullable = false, length = 20)
    private String arAccountCode = "1100";

    @Column(name = "revenue_account_code", nullable = false, length = 20)
    private String revenueAccountCode = "4000";

    @Column(name = "vat_account_code", length = 20)
    private String vatAccountCode;

    @Column(name = "bank_account_code", length = 20)
    private String bankAccountCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FmProfile create(TenantId tenantId, String companyName, String registrationNumber,
                                   String contactEmail, String contactPhone, String notes) {
        FmProfile p = new FmProfile();
        p.tenantId = tenantId;
        p.companyName = companyName;
        p.registrationNumber = registrationNumber;
        p.contactEmail = contactEmail;
        p.contactPhone = contactPhone;
        p.notes = notes;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String companyName, String registrationNumber, String contactEmail,
                       String contactPhone, String notes) {
        if (companyName != null) this.companyName = companyName;
        this.registrationNumber = registrationNumber;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    /**
     * Partial update: a null argument leaves that setting unchanged, so a client that only sends the core
     * profile fields can never wipe the VAT configuration. Validates the resulting combination.
     */
    public void updateBillingSettings(Boolean vatRegistered, String vatNumber, BigDecimal vatRate,
                                      Integer paymentTermsDays, String arAccountCode, String revenueAccountCode,
                                      String vatAccountCode, String bankAccountCode) {
        if (vatRegistered != null) this.vatRegistered = vatRegistered;
        if (vatNumber != null) this.vatNumber = blankToNull(vatNumber);
        if (vatRate != null) this.vatRate = vatRate;
        if (paymentTermsDays != null) this.paymentTermsDays = paymentTermsDays;
        if (arAccountCode != null && !arAccountCode.isBlank()) this.arAccountCode = arAccountCode.trim();
        if (revenueAccountCode != null && !revenueAccountCode.isBlank()) this.revenueAccountCode = revenueAccountCode.trim();
        if (vatAccountCode != null) this.vatAccountCode = blankToNull(vatAccountCode);
        if (bankAccountCode != null) this.bankAccountCode = blankToNull(bankAccountCode);

        if (this.vatRate.signum() < 0 || this.vatRate.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("vatRate must be between 0 and 100");
        if (this.paymentTermsDays < 0 || this.paymentTermsDays > 365)
            throw new IllegalArgumentException("paymentTermsDays must be between 0 and 365");
        if (this.vatRegistered) {
            if (this.vatNumber == null)
                throw new IllegalArgumentException("A VAT-registered company must have a VAT number");
            if (this.vatAccountCode == null)
                throw new IllegalArgumentException("A VAT-registered company must have a VAT ledger account code (where output VAT is posted)");
        }
        this.updatedAt = Instant.now();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}