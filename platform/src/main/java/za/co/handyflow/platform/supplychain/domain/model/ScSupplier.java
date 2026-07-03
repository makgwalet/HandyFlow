package za.co.handyflow.platform.supplychain.domain.model;

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

    // Banking (for payment runs — stored encrypted in production via column-level encryption)
    @Column(name = "bank_name")        String bankName;
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

    /**
     * WHY @Enumerated(EnumType.STRING)?
     * EnumType.ORDINAL stores 0, 1, 2 ... Adding a new enum value in the
     * middle shifts all ordinals and corrupts existing data silently.
     * EnumType.STRING stores "ACTIVE", "INACTIVE" — safe to reorder enum values.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    SupplierStatus status = SupplierStatus.ACTIVE;

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

    public void update(String name, String contactName, String contactEmail,
                       String contactPhone, Integer bbbeeLevel, LocalDate bbbeeExpiry,
                       Integer paymentTermsDays, SupplierStatus status, String notes) {
        if (name            != null) this.name             = name;
        if (contactName     != null) this.contactName      = contactName;
        if (contactEmail    != null) this.contactEmail     = contactEmail;
        if (contactPhone    != null) this.contactPhone     = contactPhone;
        if (bbbeeLevel      != null) this.bbbeeLevel       = bbbeeLevel;
        if (bbbeeExpiry     != null) this.bbbeeExpiry      = bbbeeExpiry;
        if (paymentTermsDays!= null) this.paymentTermsDays = paymentTermsDays;
        if (status          != null) this.status           = status;
        if (notes           != null) this.notes            = notes;
        this.updatedAt = Instant.now();
    }

    /** Soft delete — preserves historical PO records linked to this supplier. */
    public void softDelete() {
        this.status    = SupplierStatus.INACTIVE;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
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