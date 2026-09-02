package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The 3PL operator's own practice profile — one per tenant, same shell-
 * entity role as CollAgencyProfile/RecAgencyProfile. Carries the
 * operator's default rate card: a client without its own override (see
 * WhseClient/WhseItem) is billed at these rates. Unlike CollAgencyProfile,
 * there's no regulatory-registration tracking here — operating a
 * warehouse isn't a licensed activity the way third-party debt collection
 * is, so this profile is deliberately simpler.
 */
@Entity
@Table(name = "whse_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "warehouse_name", nullable = false)
    private String warehouseName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "default_storage_rate_per_unit_per_month", precision = 12, scale = 4)
    private BigDecimal defaultStorageRatePerUnitPerMonth;

    @Column(name = "default_receiving_fee_per_unit", precision = 12, scale = 4)
    private BigDecimal defaultReceivingFeePerUnit;

    @Column(name = "default_pick_fee_per_unit", precision = 12, scale = 4)
    private BigDecimal defaultPickFeePerUnit;

    @Column(name = "default_pack_fee_per_order", precision = 12, scale = 2)
    private BigDecimal defaultPackFeePerOrder;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "physical_address", columnDefinition = "TEXT")
    private String physicalAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WhseProfile create(UUID tenantId, String warehouseName, String registrationNumber,
                                      BigDecimal defaultStorageRatePerUnitPerMonth,
                                      BigDecimal defaultReceivingFeePerUnit, BigDecimal defaultPickFeePerUnit,
                                      BigDecimal defaultPackFeePerOrder, String contactEmail, String contactPhone,
                                      String physicalAddress) {
        WhseProfile p = new WhseProfile();
        p.tenantId = tenantId;
        p.warehouseName = warehouseName;
        p.registrationNumber = registrationNumber;
        p.defaultStorageRatePerUnitPerMonth = defaultStorageRatePerUnitPerMonth;
        p.defaultReceivingFeePerUnit = defaultReceivingFeePerUnit;
        p.defaultPickFeePerUnit = defaultPickFeePerUnit;
        p.defaultPackFeePerOrder = defaultPackFeePerOrder;
        p.contactEmail = contactEmail;
        p.contactPhone = contactPhone;
        p.physicalAddress = physicalAddress;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String warehouseName, String registrationNumber,
                        BigDecimal defaultStorageRatePerUnitPerMonth, BigDecimal defaultReceivingFeePerUnit,
                        BigDecimal defaultPickFeePerUnit, BigDecimal defaultPackFeePerOrder, String contactEmail,
                        String contactPhone, String physicalAddress) {
        this.warehouseName = warehouseName;
        this.registrationNumber = registrationNumber;
        this.defaultStorageRatePerUnitPerMonth = defaultStorageRatePerUnitPerMonth;
        this.defaultReceivingFeePerUnit = defaultReceivingFeePerUnit;
        this.defaultPickFeePerUnit = defaultPickFeePerUnit;
        this.defaultPackFeePerOrder = defaultPackFeePerOrder;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.physicalAddress = physicalAddress;
        this.updatedAt = Instant.now();
    }
}
