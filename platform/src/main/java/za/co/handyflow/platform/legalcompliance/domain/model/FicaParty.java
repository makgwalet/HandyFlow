package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A customer or other party being identified and verified. PENDING → VERIFIED, or ENHANCED_REQUIRED (high risk / PEP) → VERIFIED, or DECLINED.
 *  - The person who verifies must not be the person who onboarded the party (unconditional).
 *  - A sanctions-screening date is required before verification.
 *  - A company or trust needs at least one beneficial owner, and every recorded one must be verified.
 *  - A high-risk party or a politically exposed person (PEP) needs a named senior approver who is not the verifier.
 *  - identifier_hint holds at most the last few characters of an ID or registration number; do not store full identity numbers here.
 */
@Entity
@Table(name = "legalcompliance_fica_parties")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FicaParty extends AggregateRoot<FicaParty> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "party_name", nullable = false, length = 255) private String partyName;
    @Enumerated(EnumType.STRING) @Column(name = "party_type", nullable = false, length = 12) private FicaPartyType partyType;
    @Column(name = "identifier_hint", length = 20) private String identifierHint;
    @Enumerated(EnumType.STRING) @Column(name = "risk_rating", nullable = false, length = 10) private RiskLevel riskRating;
    @Column(nullable = false) private boolean pep;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FicaPartyStatus status = FicaPartyStatus.PENDING;
    @Column(name = "sanctions_screened_on") private LocalDate sanctionsScreenedOn;
    @Column(name = "verified_on") private LocalDate verifiedOn;
    @Column(name = "verified_by_id") private UUID verifiedById;
    @Column(name = "verified_by_name", length = 255) private String verifiedByName;
    @Column(name = "senior_approval_name", length = 255) private String seniorApprovalName;
    @Column(name = "next_review_due") private LocalDate nextReviewDue;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static FicaParty onboard(TenantId tenantId, String reference, String name, FicaPartyType type, String hint, RiskLevel risk, boolean pep, String notes, UUID by, String byName) {
        FicaParty p = new FicaParty();
        p.initTenantId(tenantId);
        p.reference = reference;
        p.createdBy = by;
        p.createdByName = byName;
        p.apply(name, type, hint, risk, pep, notes);
        return p;
    }

    public void update(String name, FicaPartyType type, String hint, RiskLevel risk, boolean pep, String notes) {
        if (status == FicaPartyStatus.DECLINED) throw new IllegalStateException("A declined party cannot be edited");
        apply(name, type, hint, risk, pep, notes);
        if (status == FicaPartyStatus.VERIFIED && (risk == RiskLevel.HIGH || pep)) { this.status = FicaPartyStatus.ENHANCED_REQUIRED; clearVerification(); }
    }

    private void apply(String name, FicaPartyType type, String hint, RiskLevel risk, boolean pep, String notes) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A name is required");
        if (type == null) throw new IllegalArgumentException("A party type is required");
        if (risk == null) throw new IllegalArgumentException("A risk rating is required");
        if (hint != null && hint.trim().length() > 8) throw new IllegalArgumentException("Record only the last few characters of an identity or registration number (at most 8)");
        this.partyName = name.trim();
        this.partyType = type;
        this.identifierHint = hint == null || hint.isBlank() ? null : hint.trim();
        this.riskRating = risk;
        this.pep = pep;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        if (status == FicaPartyStatus.PENDING && (risk == RiskLevel.HIGH || pep)) this.status = FicaPartyStatus.ENHANCED_REQUIRED;
        else if (status == FicaPartyStatus.ENHANCED_REQUIRED && risk != RiskLevel.HIGH && !pep) this.status = FicaPartyStatus.PENDING;
    }

    public void screenedForSanctions(LocalDate on, LocalDate today) {
        if (status == FicaPartyStatus.DECLINED) throw new IllegalStateException("A declined party cannot be screened");
        if (on == null) throw new IllegalArgumentException("The screening date is required");
        if (on.isAfter(today)) throw new IllegalArgumentException("A screening date cannot be in the future");
        this.sanctionsScreenedOn = on;
    }

    /** owners: the party's current beneficial owners. nextReview is computed by the caller from the risk rating. */
    public void verify(UUID by, String byName, LocalDate today, List<FicaBeneficialOwner> owners, String seniorApprover, LocalDate nextReview) {
        if (status != FicaPartyStatus.PENDING && status != FicaPartyStatus.ENHANCED_REQUIRED) throw new IllegalStateException("Only a pending party can be verified — current: " + status);
        if (byName == null || byName.isBlank()) throw new IllegalArgumentException("A verifier is required");
        if (by != null && by.equals(createdBy)) throw new IllegalStateException("Segregation of duties: you onboarded this party, so you cannot verify it");
        if (sanctionsScreenedOn == null) throw new IllegalArgumentException("Record the sanctions screening before verifying");
        if (partyType == FicaPartyType.COMPANY || partyType == FicaPartyType.TRUST) {
            if (owners == null || owners.isEmpty()) throw new IllegalArgumentException("Record the beneficial owners of a company or trust before verifying");
            if (owners.stream().anyMatch(o -> !o.isVerified())) throw new IllegalArgumentException("Every beneficial owner must be verified first");
        }
        if (riskRating == RiskLevel.HIGH || pep) {
            if (seniorApprover == null || seniorApprover.isBlank()) throw new IllegalArgumentException("A high-risk party or a politically exposed person needs senior management approval: name the approver");
            if (seniorApprover.trim().equalsIgnoreCase(byName.trim())) throw new IllegalStateException("Segregation of duties: the senior approver must be someone other than the verifier");
            this.seniorApprovalName = seniorApprover.trim();
        } else {
            this.seniorApprovalName = null;
        }
        this.verifiedById = by;
        this.verifiedByName = byName.trim();
        this.verifiedOn = today;
        this.nextReviewDue = nextReview;
        this.status = FicaPartyStatus.VERIFIED;
    }

    /** Periodic review found the file needs redoing. */
    public void reopen(String reason) {
        if (status != FicaPartyStatus.VERIFIED) throw new IllegalStateException("Only a verified party can be reopened — current: " + status);
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say why (at least " + MIN_TEXT + " characters)");
        clearVerification();
        this.notes = (notes == null ? "" : notes + "\n") + "Reopened: " + reason.trim();
        this.status = riskRating == RiskLevel.HIGH || pep ? FicaPartyStatus.ENHANCED_REQUIRED : FicaPartyStatus.PENDING;
    }

    public void decline(String reason) {
        if (status == FicaPartyStatus.DECLINED) throw new IllegalStateException("Already declined");
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say why (at least " + MIN_TEXT + " characters)");
        clearVerification();
        this.notes = (notes == null ? "" : notes + "\n") + "Declined: " + reason.trim();
        this.status = FicaPartyStatus.DECLINED;
    }

    private void clearVerification() {
        this.verifiedOn = null;
        this.verifiedById = null;
        this.verifiedByName = null;
        this.seniorApprovalName = null;
        this.nextReviewDue = null;
    }
}
