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
 * The agency's own practice profile — one per tenant, same shell-entity
 * role as RecAgencyProfile/AccountantProfile/BookAgencyProfile. Carries
 * the agency's own Debt Collectors Act FIRM registration (renewal
 * tracked by CollectionsAgencyNotificationScheduler) and the default
 * commission rate new clients get unless they negotiate an override on
 * CollAgencyClient.commissionRatePct — same "agency default, per-client
 * override" pattern RecAgencyClient.placementFeePct already established.
 * <p>
 * Individual collector registration is separate — see CollAgencyCollector
 * — because the Debt Collectors Act requires each PERSON making
 * collection contact to be individually registered, not just the firm.
 */
@Entity
@Table(name = "collagency_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "agency_name", nullable = false)
    private String agencyName;

    @Column(name = "firm_registration_number")
    private String firmRegistrationNumber; // Council for Debt Collectors firm registration

    @Column(name = "firm_registration_expiry_date")
    private LocalDate firmRegistrationExpiryDate;

    @Column(name = "default_commission_pct", precision = 5, scale = 2)
    private BigDecimal defaultCommissionPct; // e.g. 20.00 = 20%

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

    public static CollAgencyProfile create(UUID tenantId, String agencyName, String firmRegistrationNumber,
                                            LocalDate firmRegistrationExpiryDate, BigDecimal defaultCommissionPct,
                                            String contactEmail, String contactPhone, String physicalAddress) {
        CollAgencyProfile p = new CollAgencyProfile();
        p.tenantId = tenantId;
        p.agencyName = agencyName;
        p.firmRegistrationNumber = firmRegistrationNumber;
        p.firmRegistrationExpiryDate = firmRegistrationExpiryDate;
        p.defaultCommissionPct = defaultCommissionPct;
        p.contactEmail = contactEmail;
        p.contactPhone = contactPhone;
        p.physicalAddress = physicalAddress;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String agencyName, String firmRegistrationNumber, LocalDate firmRegistrationExpiryDate,
                        BigDecimal defaultCommissionPct, String contactEmail, String contactPhone,
                        String physicalAddress) {
        this.agencyName = agencyName;
        this.firmRegistrationNumber = firmRegistrationNumber;
        this.firmRegistrationExpiryDate = firmRegistrationExpiryDate;
        this.defaultCommissionPct = defaultCommissionPct;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.physicalAddress = physicalAddress;
        this.updatedAt = Instant.now();
    }
}
