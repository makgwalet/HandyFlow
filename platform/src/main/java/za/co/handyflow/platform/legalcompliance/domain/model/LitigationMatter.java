package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A dispute or piece of litigation the company is a party to — for or
 * against. matterNumber is generated via TenantSequenceService (sequence
 * name "LEGALCOMPLIANCE_MATTER"), same atomic-numbering mechanism every
 * other numbered-document type in this codebase uses.
 */
@Entity
@Table(name = "legalcompliance_litigation_matters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LitigationMatter extends AggregateRoot<LitigationMatter> {

    @Column(name = "matter_number", nullable = false, length = 30)
    private String matterNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "matter_type", nullable = false, length = 20)
    private LitigationMatterType matterType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LitigationStatus status;

    @Column(name = "opposing_party", nullable = false, length = 255)
    private String opposingParty;

    @Column(name = "our_side", length = 20)
    private String ourSide; // "CLAIMANT" or "DEFENDANT" — free text, not an enum: some matters (e.g. arbitration) don't cleanly fit either label.

    @Column(name = "estimated_exposure", precision = 15, scale = 2)
    private BigDecimal estimatedExposure;

    @Column(name = "legal_representative", length = 255)
    private String legalRepresentative;

    @Column(name = "court_or_forum", length = 255)
    private String courtOrForum;

    @Column(name = "case_reference", length = 100)
    private String caseReference;

    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate;

    @Column(name = "next_key_date")
    private LocalDate nextKeyDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "outcome_notes", columnDefinition = "TEXT")
    private String outcomeNotes;

    /** Optional link to a specific contracting.Contract (via ContractingFacade) this dispute arises from. */
    @Column(name = "linked_contract_id")
    private UUID linkedContractId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static LitigationMatter create(TenantId tenantId, String matterNumber, String title,
                                          LitigationMatterType matterType, String opposingParty, String ourSide,
                                          BigDecimal estimatedExposure, String legalRepresentative,
                                          String courtOrForum, String caseReference, LocalDate openedDate,
                                          LocalDate nextKeyDate, String description, UUID createdBy) {
        LitigationMatter m = new LitigationMatter();
        m.initTenantId(tenantId);
        m.matterNumber = matterNumber;
        m.title = title;
        m.matterType = matterType;
        m.status = LitigationStatus.OPEN;
        m.opposingParty = opposingParty;
        m.ourSide = ourSide;
        m.estimatedExposure = estimatedExposure;
        m.legalRepresentative = legalRepresentative;
        m.courtOrForum = courtOrForum;
        m.caseReference = caseReference;
        m.openedDate = openedDate;
        m.nextKeyDate = nextKeyDate;
        m.description = description;
        m.createdBy = createdBy;
        return m;
    }

    public void update(String title, String opposingParty, String ourSide, BigDecimal estimatedExposure,
                       String legalRepresentative, String courtOrForum, String caseReference,
                       LocalDate nextKeyDate, String description) {
        assertOpen();
        this.title = title;
        this.opposingParty = opposingParty;
        this.ourSide = ourSide;
        this.estimatedExposure = estimatedExposure;
        this.legalRepresentative = legalRepresentative;
        this.courtOrForum = courtOrForum;
        this.caseReference = caseReference;
        this.nextKeyDate = nextKeyDate;
        this.description = description;
    }

    public void advanceStatus(LitigationStatus newStatus) {
        assertOpen();
        this.status = newStatus;
    }

    public void close(LitigationStatus finalStatus, String outcomeNotes) {
        if (finalStatus != LitigationStatus.SETTLED && finalStatus != LitigationStatus.WITHDRAWN
                && finalStatus != LitigationStatus.CLOSED) {
            throw new IllegalArgumentException("close() requires a terminal status: SETTLED, WITHDRAWN, or CLOSED");
        }
        this.status = finalStatus;
        this.outcomeNotes = outcomeNotes;
        this.closedDate = LocalDate.now();
    }

    public void linkContract(UUID contractId) {
        this.linkedContractId = contractId;
    }

    private void assertOpen() {
        if (status == LitigationStatus.SETTLED || status == LitigationStatus.WITHDRAWN
                || status == LitigationStatus.CLOSED) {
            throw new IllegalStateException("A closed litigation matter (status: " + status + ") cannot be edited");
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(UUID deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }
}
