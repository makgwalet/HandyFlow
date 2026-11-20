package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.KycRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** A document or check the firm has verified: an identity document, proof of address, registration papers, the ownership structure, a trust deed. */
@Entity
@Table(name = "lp_kyc_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpKycItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(nullable = false, length = 20) private String kind;
    @Column(name = "party_id") private UUID partyId;
    @Column(nullable = false, length = 500) private String description;
    @Column(name = "document_ref") private String documentRef;
    @Column(name = "evidence_id") private UUID evidenceId;
    @Column(name = "verified_on", nullable = false) private LocalDate verifiedOn;
    @Column(name = "verified_by", nullable = false) private UUID verifiedBy;
    @Column(name = "verified_by_name") private String verifiedByName;

    public static LpKycItem create(TenantId tenantId, UUID profileId, String kind, UUID partyId, String description, String documentRef, UUID evidenceId,
                                   LocalDate verifiedOn, UUID by, String byName) {
        if (!KycRules.ITEM_KINDS.contains(kind)) throw new IllegalArgumentException("Choose what was verified");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Describe what was seen, for example: South African ID, number checked against the card");
        if (verifiedOn == null) throw new IllegalArgumentException("Enter the date it was verified");
        if (by == null) throw new IllegalArgumentException("Verification must say who did it");
        LpKycItem i = new LpKycItem();
        i.tenantId = tenantId;
        i.profileId = profileId;
        i.kind = kind;
        i.partyId = partyId;
        i.description = description.trim();
        i.documentRef = documentRef == null || documentRef.isBlank() ? null : documentRef.trim();
        i.evidenceId = evidenceId;
        i.verifiedOn = verifiedOn;
        i.verifiedBy = by;
        i.verifiedByName = byName;
        return i;
    }
}
