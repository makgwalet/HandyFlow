package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A supplier, partner, contractor or service provider under compliance oversight. ONBOARDING → APPROVED ⇄ SUSPENDED → OFFBOARDED.
 * Approval needs: every required item VERIFIED and not expired; if the party handles personal information, a verified OPERATOR_AGREEMENT item
 * (POPIA expects a written contract with an operator); and an approver who did not create the record (unconditional). supplier_ref optionally points at
 * a supply-chain supplier but is not a foreign key because the modules are separate.
 */
@Entity
@Table(name = "legalcompliance_third_parties")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThirdParty extends AggregateRoot<ThirdParty> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "party_type", nullable = false, length = 20) private ThirdPartyType partyType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Criticality criticality;
    @Enumerated(EnumType.STRING) @Column(name = "risk_rating", nullable = false, length = 10) private RiskLevel riskRating;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private ThirdPartyStatus status = ThirdPartyStatus.ONBOARDING;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "supplier_ref") private UUID supplierRef;
    @Column(name = "handles_personal_info", nullable = false) private boolean handlesPersonalInfo;
    @Column(name = "review_months", nullable = false) private int reviewMonths;
    @Column(name = "last_reviewed_on") private LocalDate lastReviewedOn;
    @Column(name = "next_review_due") private LocalDate nextReviewDue;
    @Column(name = "approved_by_id") private UUID approvedById;
    @Column(name = "approved_by_name", length = 255) private String approvedByName;
    @Column(name = "approved_on") private LocalDate approvedOn;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static ThirdParty create(TenantId tenantId, String reference, String name, ThirdPartyType type, Criticality crit, RiskLevel risk, String owner, UUID supplierRef,
                                    boolean personalInfo, int reviewMonths, String notes, UUID by, String byName) {
        ThirdParty t = new ThirdParty();
        t.initTenantId(tenantId);
        t.reference = reference;
        t.createdBy = by;
        t.createdByName = byName;
        t.apply(name, type, crit, risk, owner, supplierRef, personalInfo, reviewMonths, notes);
        return t;
    }

    public void update(String name, ThirdPartyType type, Criticality crit, RiskLevel risk, String owner, UUID supplierRef, boolean personalInfo, int reviewMonths, String notes) {
        if (status == ThirdPartyStatus.OFFBOARDED) throw new IllegalStateException("An offboarded third party cannot be edited");
        boolean gainedPersonalInfo = personalInfo && !handlesPersonalInfo;
        apply(name, type, crit, risk, owner, supplierRef, personalInfo, reviewMonths, notes);
        if (status == ThirdPartyStatus.APPROVED && gainedPersonalInfo) { this.status = ThirdPartyStatus.SUSPENDED; }
        if (lastReviewedOn != null) this.nextReviewDue = lastReviewedOn.plusMonths(reviewMonths);
    }

    private void apply(String name, ThirdPartyType type, Criticality crit, RiskLevel risk, String owner, UUID supplierRef, boolean personalInfo, int reviewMonths, String notes) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A name is required");
        if (type == null) throw new IllegalArgumentException("A type is required");
        if (crit == null) throw new IllegalArgumentException("A criticality is required");
        if (risk == null) throw new IllegalArgumentException("A risk rating is required");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("A relationship owner is required");
        if (reviewMonths < 1 || reviewMonths > 60) throw new IllegalArgumentException("The review period must be between 1 and 60 months");
        this.name = name.trim();
        this.partyType = type;
        this.criticality = crit;
        this.riskRating = risk;
        this.ownerName = owner.trim();
        this.supplierRef = supplierRef;
        this.handlesPersonalInfo = personalInfo;
        this.reviewMonths = reviewMonths;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    /** What stops this party being approved right now. Empty means nothing does. */
    public List<String> blockers(List<ThirdPartyItem> items, LocalDate today) {
        List<String> out = new ArrayList<>();
        for (ThirdPartyItem i : items) {
            if (!i.isRequired()) continue;
            if (i.getStatus() != ItemStatus.VERIFIED) out.add(i.getTitle() + " is " + i.getStatus().name().toLowerCase());
            else if (i.getValidTo() != null && i.getValidTo().isBefore(today)) out.add(i.getTitle() + " expired on " + i.getValidTo());
        }
        if (handlesPersonalInfo && items.stream().noneMatch(i -> i.getKind() == ItemKind.OPERATOR_AGREEMENT && i.getStatus() == ItemStatus.VERIFIED
                && (i.getValidTo() == null || !i.getValidTo().isBefore(today))))
            out.add("A verified operator agreement is required because this party handles personal information");
        if (items.stream().noneMatch(ThirdPartyItem::isRequired)) out.add("Add at least one required due-diligence item");
        return out;
    }

    public void approve(UUID by, String byName, LocalDate today, List<ThirdPartyItem> items) {
        if (status != ThirdPartyStatus.ONBOARDING && status != ThirdPartyStatus.SUSPENDED) throw new IllegalStateException("Only an onboarding or suspended party can be approved — current: " + status);
        if (byName == null || byName.isBlank()) throw new IllegalArgumentException("An approver is required");
        if (by != null && by.equals(createdBy)) throw new IllegalStateException("Segregation of duties: you created this record, so you cannot approve it");
        List<String> b = blockers(items, today);
        if (!b.isEmpty()) throw new IllegalStateException("Cannot approve yet: " + String.join("; ", b));
        this.approvedById = by;
        this.approvedByName = byName.trim();
        this.approvedOn = today;
        this.lastReviewedOn = today;
        this.nextReviewDue = today.plusMonths(reviewMonths);
        this.status = ThirdPartyStatus.APPROVED;
    }

    public void suspend(String reason) {
        if (status != ThirdPartyStatus.APPROVED) throw new IllegalStateException("Only an approved party can be suspended — current: " + status);
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say why (at least " + MIN_TEXT + " characters)");
        this.notes = (notes == null ? "" : notes + "\n") + "Suspended: " + reason.trim();
        this.status = ThirdPartyStatus.SUSPENDED;
    }

    public void offboard(String reason) {
        if (status == ThirdPartyStatus.OFFBOARDED) throw new IllegalStateException("Already offboarded");
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say why (at least " + MIN_TEXT + " characters)");
        this.notes = (notes == null ? "" : notes + "\n") + "Offboarded: " + reason.trim();
        this.status = ThirdPartyStatus.OFFBOARDED;
    }

    /** Periodic review. Does not itself approve or suspend; blockers still show on the record. */
    public void recordReview(LocalDate today, RiskLevel newRisk, String note) {
        if (status != ThirdPartyStatus.APPROVED) throw new IllegalStateException("Only an approved party is reviewed — current: " + status);
        if (note == null || note.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Record the review outcome (at least " + MIN_TEXT + " characters)");
        if (newRisk != null) this.riskRating = newRisk;
        this.lastReviewedOn = today;
        this.nextReviewDue = today.plusMonths(reviewMonths);
        this.notes = (notes == null ? "" : notes + "\n") + "Reviewed " + today + ": " + note.trim();
    }
}
