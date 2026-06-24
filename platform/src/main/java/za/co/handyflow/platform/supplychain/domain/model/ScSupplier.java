package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_suppliers")
@Getter
@NoArgsConstructor
public class ScSupplier {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(nullable = false) String name;
    @Column(name = "registration_number") String registrationNumber;
    @Column(name = "vat_number")          String vatNumber;
    @Column(name = "bbbee_level")         Integer bbbeeLevel;
    @Column(name = "bbbee_certificate")   String  bbbeeCertificate;
    @Column(name = "bbbee_expiry")        LocalDate bbbeeExpiry;
    @Column(name = "contact_name")        String contactName;
    @Column(name = "contact_email")       String contactEmail;
    @Column(name = "contact_phone")       String contactPhone;
    String website;
    String street;
    String suburb;
    String city;
    String province;
    @Column(name = "postal_code")         String postalCode;
    String country = "South Africa";
    @Column(name = "bank_name")           String bankName;
    @Column(name = "bank_account")        String bankAccount;
    @Column(name = "bank_branch_code")    String bankBranchCode;
    @Column(name = "payment_terms_days")  int paymentTermsDays = 30;
    String currency = "ZAR";
    @Column(name = "total_orders")        int totalOrders = 0;
    @Column(name = "on_time_deliveries")  int onTimeDeliveries = 0;
    @Column(name = "late_deliveries")     int lateDeliveries = 0;
    @Column(name = "defect_count")        int defectCount = 0;
    @Column(nullable = false)             String status = "ACTIVE";
    @Column(name = "preferred_categories") String preferredCategories;
    String notes;
    @Column(name = "created_by")          UUID createdBy;
    @Column(name = "created_at")          Instant createdAt;
    @Column(name = "updated_at")          Instant updatedAt;
    @Column(name = "deleted_at")          Instant deletedAt;

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
        s.status = "ACTIVE";
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void update(String name, String contactName, String contactEmail,
                       String contactPhone, Integer bbbeeLevel, LocalDate bbbeeExpiry,
                       Integer paymentTermsDays, String status, String notes) {
        if (name != null) this.name = name;
        if (contactName != null) this.contactName = contactName;
        if (contactEmail != null) this.contactEmail = contactEmail;
        if (contactPhone != null) this.contactPhone = contactPhone;
        if (bbbeeLevel != null) this.bbbeeLevel = bbbeeLevel;
        if (bbbeeExpiry != null) this.bbbeeExpiry = bbbeeExpiry;
        if (paymentTermsDays != null) this.paymentTermsDays = paymentTermsDays;
        if (status != null) this.status = status;
        if (notes != null) this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public Double getOnTimeRate() {
        if (totalOrders == 0) return null;
        return (double) onTimeDeliveries / totalOrders * 100;
    }
}
