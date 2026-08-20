package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The practice shell — one row per tenant running this module, holding
 * the bureau's own identity for branding fee notes, reminder emails, and
 * payslip letterheads sent to its clients' employees. Directly mirrors
 * AccountantProfile's role for the accountant module.
 */
@Entity
@Table(name = "pay_bureau_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayBureauProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "firm_name", nullable = false)
    private String firmName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "sdl_number")
    private String sdlNumber; // the BUREAU's own SDL number, distinct from each client's

    private String email;
    private String phone;

    @Column(name = "physical_address", columnDefinition = "TEXT")
    private String physicalAddress;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "logo_evidence_id") private java.util.UUID logoEvidenceId;
    public java.util.UUID getLogoEvidenceId() { return this.logoEvidenceId; }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PayBureauProfile create(UUID tenantId, String firmName) {
        PayBureauProfile p = new PayBureauProfile();
        p.tenantId = tenantId;
        p.firmName = firmName;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String firmName, String registrationNumber, String sdlNumber,
                       String email, String phone, String physicalAddress, String logoUrl) {
        this.firmName = firmName;
        this.registrationNumber = registrationNumber;
        this.sdlNumber = sdlNumber;
        this.email = email;
        this.phone = phone;
        this.physicalAddress = physicalAddress;
        this.logoUrl = logoUrl;
        this.updatedAt = Instant.now();
    }

    public void setLogoEvidenceId(java.util.UUID id) { this.logoEvidenceId = id;}
}