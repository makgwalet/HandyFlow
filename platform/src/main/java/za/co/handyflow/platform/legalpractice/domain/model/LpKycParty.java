package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.KycRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A person connected to a client whose identity the firm must establish: a beneficial owner, authorised person, trustee, founder, beneficiary or partner. */
@Entity
@Table(name = "lp_kyc_parties")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpKycParty {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(nullable = false, length = 20) private String role;
    @Column(nullable = false) private String name;
    @Column(name = "id_number", length = 100) private String idNumber;
    @Column(name = "ownership_percent", precision = 5, scale = 2) private BigDecimal ownershipPercent;
    @Column(name = "owner_method", length = 10) private String ownerMethod;
    @Column(name = "pep_status", length = 5) private String pepStatus;
    @Column(name = "verified_on") private LocalDate verifiedOn;
    @Column(name = "verified_by") private UUID verifiedBy;
    @Column(name = "verified_by_name") private String verifiedByName;
    @Column private String note;

    public static LpKycParty create(TenantId tenantId, UUID profileId, String role, String name, String idNumber, BigDecimal percent, String method,
                                    String pep, LocalDate verifiedOn, UUID by, String byName, String note) {
        if (!KycRules.PARTY_ROLES.contains(role)) throw new IllegalArgumentException("Choose the person's role");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Enter the person's full name");
        if (percent != null && (percent.signum() <= 0 || percent.compareTo(new BigDecimal("100")) > 0)) throw new IllegalArgumentException("Ownership must be above 0 and at most 100 percent");
        if (method != null && !KycRules.OWNER_METHODS.contains(method)) throw new IllegalArgumentException("Choose how the owner was identified");
        if ("BENEFICIAL_OWNER".equals(role) && method == null) throw new IllegalArgumentException("Say how this owner was identified: ownership, control or management");
        if (pep != null && !KycRules.PEP.contains(pep)) throw new IllegalArgumentException("Politically exposed status must be NONE, DPEP, PIP or FPEP");
        if (verifiedOn != null && by == null) throw new IllegalArgumentException("Verification must say who did it");
        LpKycParty p = new LpKycParty();
        p.tenantId = tenantId;
        p.profileId = profileId;
        p.role = role;
        p.name = name.trim();
        p.idNumber = idNumber == null || idNumber.isBlank() ? null : idNumber.trim();
        p.ownershipPercent = percent;
        p.ownerMethod = method;
        p.pepStatus = pep;
        p.verifiedOn = verifiedOn;
        p.verifiedBy = verifiedOn == null ? null : by;
        p.verifiedByName = verifiedOn == null ? null : byName;
        p.note = note == null || note.isBlank() ? null : note.trim();
        return p;
    }

    public boolean verified() { return verifiedOn != null; }

    public void verify(LocalDate on, UUID by, String byName) {
        if (on == null) throw new IllegalArgumentException("Enter the date the identity was verified");
        if (by == null) throw new IllegalArgumentException("Verification must say who did it");
        this.verifiedOn = on;
        this.verifiedBy = by;
        this.verifiedByName = byName;
    }

    /** The same person for a review: details kept, verification cleared so it has to be done again. */
    public LpKycParty reviewCopy(UUID newProfileId) {
        return create(tenantId, newProfileId, role, name, idNumber, ownershipPercent, ownerMethod, pepStatus, null, null, null, note);
    }
}
