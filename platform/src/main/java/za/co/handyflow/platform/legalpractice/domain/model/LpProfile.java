package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The firm's own practice profile — one row per tenant, mirrors
 * {@code AccountantProfile}'s shape exactly (firmName/vatNumber/
 * practiceNumber/contactEmail/contactPhone, confirmed to have NO address
 * field on that entity either). Two bank-account display fields are added
 * beyond AccountantProfile's own set, because a legal practice's defining
 * feature — unlike an accounting practice — is that it operates two
 * legally distinct accounts (trust and business), and both need to be
 * identifiable on invoices/trust statements. These are DISPLAY fields
 * only: no bank integration, no real funds movement — same posture as
 * every other "captured, not verified" identifier in this codebase
 * (e.g. CollAgencyProfile's own registration numbers).
 */
@Entity
@Table(name = "lp_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "firm_name", nullable = false)
    private String firmName;

    @Column(name = "practice_number")
    private String practiceNumber; // Legal Practice Council Fidelity Fund Certificate number

    @Column(name = "vat_number")
    private String vatNumber;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "trust_bank_name")
    private String trustBankName;

    @Column(name = "trust_account_number")
    private String trustAccountNumber;

    @Column(name = "business_bank_name")
    private String businessBankName;

    @Column(name = "business_account_number")
    private String businessAccountNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpProfile create(TenantId tenantId, String firmName, String practiceNumber,
                                    String vatNumber, String contactEmail, String contactPhone,
                                    String trustBankName, String trustAccountNumber,
                                    String businessBankName, String businessAccountNumber) {
        LpProfile p = new LpProfile();
        p.tenantId = tenantId;
        p.firmName = firmName;
        p.practiceNumber = practiceNumber;
        p.vatNumber = vatNumber;
        p.contactEmail = contactEmail;
        p.contactPhone = contactPhone;
        p.trustBankName = trustBankName;
        p.trustAccountNumber = trustAccountNumber;
        p.businessBankName = businessBankName;
        p.businessAccountNumber = businessAccountNumber;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String firmName, String practiceNumber, String vatNumber,
                        String contactEmail, String contactPhone,
                        String trustBankName, String trustAccountNumber,
                        String businessBankName, String businessAccountNumber) {
        this.firmName = firmName;
        this.practiceNumber = practiceNumber;
        this.vatNumber = vatNumber;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.trustBankName = trustBankName;
        this.trustAccountNumber = trustAccountNumber;
        this.businessBankName = businessBankName;
        this.businessAccountNumber = businessAccountNumber;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
