package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One due-diligence item, certificate, operator agreement or insurance cover for a third party. PENDING → VERIFIED / REJECTED.
 * A certificate or insurance cover needs an expiry date; an item cannot be verified if it has already expired. Editing a verified item sends it back to PENDING.
 * Evidence files for an item are held by the shared evidence module under entity type THIRD_PARTY_ITEM.
 */
@Entity
@Table(name = "legalcompliance_third_party_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThirdPartyItem extends AggregateRoot<ThirdPartyItem> {

    public static final int MIN_TEXT = 15;

    @Column(name = "third_party_id", nullable = false) private UUID thirdPartyId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ItemKind kind;
    @Column(nullable = false, length = 255) private String title;
    @Column(length = 255) private String issuer;
    @Column(name = "reference_no", length = 100) private String referenceNo;
    @Column(nullable = false) private boolean required = true;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ItemStatus status = ItemStatus.PENDING;
    @Column(name = "valid_to") private LocalDate validTo;
    @Column(name = "verified_by_name", length = 255) private String verifiedByName;
    @Column(name = "verified_on") private LocalDate verifiedOn;
    @Column(columnDefinition = "TEXT") private String note;

    public static ThirdPartyItem add(TenantId tenantId, UUID thirdPartyId, ItemKind kind, String title, String issuer, String referenceNo, boolean required, LocalDate validTo) {
        ThirdPartyItem i = new ThirdPartyItem();
        i.initTenantId(tenantId);
        i.thirdPartyId = thirdPartyId;
        i.apply(kind, title, issuer, referenceNo, required, validTo);
        return i;
    }

    public void update(ItemKind kind, String title, String issuer, String referenceNo, boolean required, LocalDate validTo) {
        apply(kind, title, issuer, referenceNo, required, validTo);
        this.status = ItemStatus.PENDING;
        this.verifiedByName = null;
        this.verifiedOn = null;
    }

    private void apply(ItemKind kind, String title, String issuer, String referenceNo, boolean required, LocalDate validTo) {
        if (kind == null) throw new IllegalArgumentException("An item kind is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if ((kind == ItemKind.CERTIFICATE || kind == ItemKind.INSURANCE) && validTo == null) throw new IllegalArgumentException("A certificate or insurance cover needs an expiry date");
        this.kind = kind;
        this.title = title.trim();
        this.issuer = issuer == null || issuer.isBlank() ? null : issuer.trim();
        this.referenceNo = referenceNo == null || referenceNo.isBlank() ? null : referenceNo.trim();
        this.required = required;
        this.validTo = validTo;
    }

    public void verify(String by, LocalDate today) {
        if (status == ItemStatus.VERIFIED) throw new IllegalStateException("Already verified");
        if (by == null || by.isBlank()) throw new IllegalArgumentException("A verifier is required");
        if (validTo != null && validTo.isBefore(today)) throw new IllegalArgumentException("This item expired on " + validTo + " and cannot be verified");
        this.status = ItemStatus.VERIFIED;
        this.verifiedByName = by.trim();
        this.verifiedOn = today;
        this.note = null;
    }

    public void reject(String reason) {
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say why (at least " + MIN_TEXT + " characters)");
        this.status = ItemStatus.REJECTED;
        this.note = reason.trim();
        this.verifiedByName = null;
        this.verifiedOn = null;
    }
}
