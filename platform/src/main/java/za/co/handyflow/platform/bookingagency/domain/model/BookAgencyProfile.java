package za.co.handyflow.platform.bookingagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The practice shell — one row per tenant running this module, holding
 * the agency's own identity for branding client communications.
 * Directly mirrors PayBureauProfile/RecAgencyProfile's role.
 */
@Entity
@Table(name = "booka_agency_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookAgencyProfile {

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BookAgencyProfile create(UUID tenantId, String agencyName) {
        BookAgencyProfile p = new BookAgencyProfile();
        p.tenantId = tenantId;
        p.agencyName = agencyName;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String agencyName, String registrationNumber, String email,
                       String phone, String physicalAddress, String logoUrl) {
        this.agencyName = agencyName;
        this.registrationNumber = registrationNumber;
        this.email = email;
        this.phone = phone;
        this.physicalAddress = physicalAddress;
        this.logoUrl = logoUrl;
        this.updatedAt = Instant.now();
    }
}