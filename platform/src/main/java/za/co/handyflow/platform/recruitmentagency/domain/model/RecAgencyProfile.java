package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The practice shell — one row per tenant running this module, holding
 * the agency's own identity for branding placement invoices and client
 * communications. Directly mirrors PayBureauProfile's role.
 */
@Entity
@Table(name = "reca_agency_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecAgencyProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "agency_name", nullable = false)
    private String agencyName;

    @Column(name = "registration_number")
    private String registrationNumber;

    private String email;
    private String phone;

    @Column(name = "physical_address", columnDefinition = "TEXT")
    private String physicalAddress;

    @Column(name = "logo_url")
    private String logoUrl;

    // Default placement fee, as a percentage of the candidate's annual
    // salary — the agency's standard rate before any client-specific
    // override (see RecAgencyClient.placementFeePct). Common industry
    // range is 15-20%; 15% chosen as a reasonable, editable default,
    // not a researched market figure.
    @Column(name = "default_placement_fee_pct", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal defaultPlacementFeePct = new java.math.BigDecimal("15.00");

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static RecAgencyProfile create(UUID tenantId, String agencyName) {
        RecAgencyProfile p = new RecAgencyProfile();
        p.tenantId = tenantId;
        p.agencyName = agencyName;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String agencyName, String registrationNumber, String email,
                       String phone, String physicalAddress, String logoUrl,
                       java.math.BigDecimal defaultPlacementFeePct) {
        this.agencyName = agencyName;
        this.registrationNumber = registrationNumber;
        this.email = email;
        this.phone = phone;
        this.physicalAddress = physicalAddress;
        this.logoUrl = logoUrl;
        if (defaultPlacementFeePct != null) this.defaultPlacementFeePct = defaultPlacementFeePct;
        this.updatedAt = Instant.now();
    }
}