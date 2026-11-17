package za.co.handyflow.platform.supplychain.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_suppliers")
@Getter
@NoArgsConstructor
public class ScSupplier {

    @Id UUID id;
    @Column(name = "tenant_id",           nullable = false) UUID tenantId;
    @Column(nullable = false)                               String name;
    @Column(name = "registration_number")                  String registrationNumber;
    @Column(name = "vat_number")                           String vatNumber;

    // BBBEE — mandatory for South African public sector procurement
    @Column(name = "bbbee_level")       Integer   bbbeeLevel;
    @Column(name = "bbbee_certificate") String    bbbeeCertificate;
    @Column(name = "bbbee_expiry")      LocalDate bbbeeExpiry;

    // Contact
    @Column(name = "contact_name")  String contactName;
    @Column(name = "contact_email") String contactEmail;
    @Column(name = "contact_phone") String contactPhone;
    String website;

    // Address
    String street;
    String suburb;
    String city;
    String province;
    @Column(name = "postal_code") String postalCode;
    String country = "South Africa";

    // Banking. CHANGED (chunk 4): bankAccount is never serialised to API clients (it was
    // previously returned in full to every SCM_READ user) — they get bankAccountMasked.
    // After creation these fields change ONLY via a verified SupplierBankChange.
    @Column(name = "bank_name")        String bankName;
    @JsonIgnore
    @Column(name = "bank_account")     String bankAccount;
    @Column(name = "bank_branch_code") String bankBranchCode;

    // Procurement terms
    @Column(name = "payment_terms_days") int    paymentTermsDays = 30;
    String currency = "ZAR";

    // Performance counters (denormalised for dashboard speed — updated via ScmService)
    @Column(name = "total_orders")       int totalOrders       = 0;
    @Column(name = "on_time_deliveries") int onTimeDeliveries  = 0;
    @Column(name = "late_deliveries")    int lateDeliveries    = 0;
    @Column(name = "defect_count")       int defectCount       = 0;

    /** EnumType.STRING: safe to reorder/extend the enum without corrupting stored data. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    SupplierStatus status = SupplierStatus.ACTIVE;

    // NEW (chunk 4): why / when / by whom the supplier last changed status.
    @Column(name = "status_reason", length = 500) String statusReason;
    @Column(name = "status_changed_at") Instant statusChangedAt;
    @Column(name = "status_changed_by") UUID statusChangedBy;

    @Column(name = "preferred_categories") String preferredCategories;
    String notes;
    @Column(name = "created_by") UUID    createdBy;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ScSupplier create(UUID tenantId, String name, String registrationNumber,
                                    String vatNumber, Integer bbbeeLevel, LocalDate bbbeeExpiry,
                                    String contactName, String contactEmail, String contactPhone,
                                    String website, String street, String suburb, String city,
                                    String province, String postalCode, String bankName,
                                    String bankAccount, String bankBranchCode,
                                    int paymentTermsDays, String currency, String notes,
                                    UUID createdBy) {
        ScSupplier s = new ScSupplier();
        s.id = UUID.randomUUID();
        s.tenantId = tenantId;
        s.name = name;
        s.registrationNumber = registrationNumber;
        s.vatNumber = vatNumber;
        s.bbbeeLevel = bbbeeLevel;
        s.bbbeeExpiry = bbbeeExpiry;
        s.contactName = contactName;
        s.contactEmail = contactEmail;
        s.contactPhone = contactPhone;
        s.website = website;
        s.street = street;
        s.suburb = suburb;
        s.city = city;
        s.province = province;
        s.postalCode = postalCode;
        s.bankName = bankName;
        s.bankAccount = bankAccount;
        s.bankBranchCode = bankBranchCode;
        s.paymentTermsDays = paymentTermsDays > 0 ? paymentTermsDays : 30;
        s.currency = currency != null ? currency : "ZAR";
        s.notes = notes;
        s.createdBy = createdBy;
        s.status = SupplierStatus.ACTIVE;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    /**
     * Profile edit. CHANGED (chunk 4): no longer takes a status — status was editable here
     * by anyone with SCM_ADMIN, which let a BLACKLISTED supplier be flipped back to ACTIVE
     * with no reason and no audit. Use transitionTo() via SupplierLifecycleService.
     */
    public void update(String name, String contactName, String contactEmail,
                       String contactPhone, Integer bbbeeLevel, LocalDate bbbeeExpiry,
                       Integer paymentTermsDays, String notes) {
        if (name            != null) this.name             = name;
        if (contactName     != null) this.contactName      = contactName;
        if (contactEmail    != null) this.contactEmail     = contactEmail;
        if (contactPhone    != null) this.contactPhone     = contactPhone;
        if (bbbeeLevel      != null) this.bbbeeLevel       = bbbeeLevel;
        if (bbbeeExpiry     != null) this.bbbeeExpiry      = bbbeeExpiry;
        if (paymentTermsDays!= null) this.paymentTermsDays = paymentTermsDays;
        if (notes           != null) this.notes            = notes;
        this.updatedAt = Instant.now();
    }

    /** Validated lifecycle move; the service has already checked reasons, actor and segregation of duties. */
    public void transitionTo(SupplierStatus target, String reason, UUID by) {
        if (!status.canTransitionTo(target))
            throw new IllegalStateException("A " + status + " supplier cannot move to " + target
                    + " (allowed: " + status.allowedNext() + ")");
        this.status = target;
        this.statusReason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.statusChangedAt = Instant.now();
        this.statusChangedBy = by;
        this.updatedAt = Instant.now();
    }

    /** Called straight after create() when the tenant requires supplier approval before first use. */
    public void requireApproval() {
        this.status = SupplierStatus.PENDING_APPROVAL;
        this.statusReason = "Awaiting approval";
        this.statusChangedAt = Instant.now();
    }

    /** Only ever called by SupplierBankChangeService after independent verification. */
    public void applyBankDetails(String bankName, String bankAccount, String bankBranchCode) {
        this.bankName = bankName;
        this.bankAccount = bankAccount;
        this.bankBranchCode = bankBranchCode;
        this.updatedAt = Instant.now();
    }

    /** Soft delete — preserves historical PO records linked to this supplier. */
    public void softDelete() {
        this.status    = SupplierStatus.INACTIVE;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** What API clients see instead of the account number, e.g. "****4321". */
    public String getBankAccountMasked() {
        if (bankAccount == null || bankAccount.isBlank()) return null;
        String digits = bankAccount.trim();
        return digits.length() <= 4 ? "****" : "****" + digits.substring(digits.length() - 4);
    }

    /** @return on-time delivery rate as a percentage, or null if no orders yet. */
    public Double getOnTimeRate() {
        if (totalOrders == 0) return null;
        return (double) onTimeDeliveries / totalOrders * 100.0;
    }

    /** True when this supplier can receive new purchase orders. */
    public boolean isOrderable() {
        return status == SupplierStatus.ACTIVE && deletedAt == null;
    }
}
