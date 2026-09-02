package za.co.handyflow.platform.warehousing.domain.model;

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
 * A client business whose goods this operator stores and fulfills —
 * direct structural mirror of CollAgencyClient/RecAgencyClient. Rate
 * fields are per-client OVERRIDES of WhseProfile's own defaults — null
 * means "use the operator default," same override pattern established
 * across every provider module in this codebase
 * (RecAgencyClient.placementFeePct, CollAgencyClient.commissionRatePct).
 * A further override can sit on WhseItem itself (see that entity) for a
 * client whose SKUs have genuinely different storage costs (e.g. bulky
 * vs. small items) — resolution order is item -&gt; client -&gt; profile,
 * see WhseBillingService.resolveStorageRate().
 */
@Entity
@Table(name = "whse_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the WAREHOUSE OPERATOR's tenant

    @Column(name = "trading_name", nullable = false)
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "storage_rate_per_unit_per_month", precision = 12, scale = 4)
    private BigDecimal storageRatePerUnitPerMonth; // null = use profile default

    @Column(name = "receiving_fee_per_unit", precision = 12, scale = 4)
    private BigDecimal receivingFeePerUnit;

    @Column(name = "pick_fee_per_unit", precision = 12, scale = 4)
    private BigDecimal pickFeePerUnit;

    @Column(name = "pack_fee_per_order", precision = 12, scale = 2)
    private BigDecimal packFeePerOrder;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

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

    public static WhseClient create(UUID tenantId, String tradingName, String registrationNumber,
                                     BigDecimal storageRatePerUnitPerMonth, BigDecimal receivingFeePerUnit,
                                     BigDecimal pickFeePerUnit, BigDecimal packFeePerOrder, String contactName,
                                     String contactEmail, String contactPhone, String address) {
        WhseClient c = new WhseClient();
        c.tenantId = tenantId;
        c.tradingName = tradingName.trim();
        c.registrationNumber = registrationNumber;
        c.storageRatePerUnitPerMonth = storageRatePerUnitPerMonth;
        c.receivingFeePerUnit = receivingFeePerUnit;
        c.pickFeePerUnit = pickFeePerUnit;
        c.packFeePerOrder = packFeePerOrder;
        c.contactName = contactName;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.address = address;
        c.onboardedAt = LocalDate.now();
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String tradingName, String registrationNumber, BigDecimal storageRatePerUnitPerMonth,
                        BigDecimal receivingFeePerUnit, BigDecimal pickFeePerUnit, BigDecimal packFeePerOrder,
                        String contactName, String contactEmail, String contactPhone, String address,
                        String notes) {
        this.tradingName = tradingName.trim();
        this.registrationNumber = registrationNumber;
        this.storageRatePerUnitPerMonth = storageRatePerUnitPerMonth;
        this.receivingFeePerUnit = receivingFeePerUnit;
        this.pickFeePerUnit = pickFeePerUnit;
        this.packFeePerOrder = packFeePerOrder;
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

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
