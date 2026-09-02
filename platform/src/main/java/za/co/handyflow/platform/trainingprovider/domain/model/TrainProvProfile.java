package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The provider's own practice profile — one per tenant. Matches the
 * plain-entity provider-module convention (see this module's own
 * package-info.java): {@code @Id private UUID id = UUID.randomUUID();},
 * boxed {@code @Version Long version}, no shared superclass.
 */
@Entity
@Table(name = "trainprov_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "trading_name", nullable = false)
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    /** e.g. a SETA name — "Services SETA", "MERSETA". Free text — the catalogue of accreditation bodies is provider-specific. */
    @Column(name = "accreditation_body")
    private String accreditationBody;

    @Column(name = "accreditation_number")
    private String accreditationNumber;

    @Column(name = "accreditation_expiry")
    private java.time.LocalDate accreditationExpiry;

    private String address;
    private String phone;
    private String email;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static TrainProvProfile create(TenantId tenantId, String tradingName, String registrationNumber,
                                           String accreditationBody, String accreditationNumber,
                                           java.time.LocalDate accreditationExpiry, String address,
                                           String phone, String email) {
        TrainProvProfile p = new TrainProvProfile();
        p.tenantId = tenantId.getValue();
        p.tradingName = tradingName;
        p.registrationNumber = registrationNumber;
        p.accreditationBody = accreditationBody;
        p.accreditationNumber = accreditationNumber;
        p.accreditationExpiry = accreditationExpiry;
        p.address = address;
        p.phone = phone;
        p.email = email;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String tradingName, String registrationNumber, String accreditationBody,
                        String accreditationNumber, java.time.LocalDate accreditationExpiry,
                        String address, String phone, String email, String logoUrl) {
        this.tradingName = tradingName;
        this.registrationNumber = registrationNumber;
        this.accreditationBody = accreditationBody;
        this.accreditationNumber = accreditationNumber;
        this.accreditationExpiry = accreditationExpiry;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.logoUrl = logoUrl;
        this.updatedAt = Instant.now();
    }

    public boolean isAccreditationExpiringWithin(int days) {
        return this.accreditationExpiry != null
                && !this.accreditationExpiry.isAfter(java.time.LocalDate.now().plusDays(days));
    }
}
