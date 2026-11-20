package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.LitigationRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The court case on a matter: where it is, which stage it has reached, who is on the other side, and how it ended. One per matter; never deleted. */
@Entity
@Table(name = "lp_litigation_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpLitigationCase {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "matter_id", nullable = false, updatable = false) private UUID matterId;
    @Column(name = "client_id", nullable = false, updatable = false) private UUID clientId;
    @Column(name = "court_level", nullable = false, length = 20) private String courtLevel;
    @Column(name = "court_name", nullable = false) private String courtName;
    @Column(name = "case_number") private String caseNumber;
    @Column(name = "our_role", nullable = false, length = 12) private String ourRole;
    @Column(nullable = false, length = 12) private String stage = "PRE_ACTION";
    @Column(name = "opposing_party") private String opposingParty;
    @Column(name = "opposing_attorney") private String opposingAttorney;
    @Column(name = "opposing_attorney_contact") private String opposingAttorneyContact;
    private String judge;
    @Column(name = "issued_on") private LocalDate issuedOn;
    @Column(name = "served_on") private LocalDate servedOn;
    @Column(length = 12) private String outcome;
    @Column(name = "concluded_on") private LocalDate concludedOn;
    @Column(name = "outcome_note") private String outcomeNote;
    private String notes;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "created_by_name", updatable = false) private String createdByName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Version
    private Long version;

    public static LpLitigationCase open(TenantId tenantId, UUID matterId, UUID clientId, String courtLevel, String courtName, String ourRole, String caseNumber, String stage,
                                        String opposingParty, String opposingAttorney, String opposingAttorneyContact, String judge, LocalDate issuedOn, LocalDate servedOn,
                                        String notes, UUID by, String byName, LocalDate today, Instant now) {
        String problem = LitigationRules.caseProblem(courtLevel, courtName, ourRole, caseNumber, stage, issuedOn, servedOn, today);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (by == null) throw new IllegalArgumentException("A case must say who opened it");
        LpLitigationCase c = new LpLitigationCase();
        c.tenantId = tenantId; c.matterId = matterId; c.clientId = clientId; c.stage = stage;
        c.createdBy = by; c.createdByName = byName; c.createdAt = now; c.updatedAt = now;
        c.apply(courtLevel, courtName, ourRole, caseNumber, opposingParty, opposingAttorney, opposingAttorneyContact, judge, issuedOn, servedOn, notes);
        return c;
    }

    public boolean isOpen() { return LitigationRules.open(stage); }

    public void requireOpen() { if (!isOpen()) throw new IllegalStateException("This case is concluded. Reopen it first"); }

    public void edit(String courtLevel, String courtName, String ourRole, String caseNumber, String opposingParty, String opposingAttorney, String opposingAttorneyContact,
                     String judge, LocalDate issuedOn, LocalDate servedOn, String notes, LocalDate today, Instant now) {
        requireOpen();
        String problem = LitigationRules.caseProblem(courtLevel, courtName, ourRole, caseNumber, stage, issuedOn, servedOn, today);
        if (problem != null) throw new IllegalArgumentException(problem);
        apply(courtLevel, courtName, ourRole, caseNumber, opposingParty, opposingAttorney, opposingAttorneyContact, judge, issuedOn, servedOn, notes);
        updatedAt = now;
    }

    /** Moves to another stage. Returns the stage it was at. A case number is needed once it leaves pre-action. */
    public String changeStage(String to, String note, Instant now) {
        requireOpen();
        String problem = LitigationRules.stageProblem(stage, to, note);
        if (problem != null) throw new IllegalStateException(problem);
        if (!"PRE_ACTION".equals(to) && (caseNumber == null || caseNumber.trim().length() < 2)) throw new IllegalStateException("Enter the case number before moving past pre-action");
        String from = stage;
        stage = to; updatedAt = now;
        return from;
    }

    public void conclude(String outcome, LocalDate on, String note, int scheduledHearings, LocalDate today, Instant now) {
        requireOpen();
        String problem = LitigationRules.concludeProblem(outcome, on, note, issuedOn, today);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (scheduledHearings > 0) throw new IllegalStateException("There " + (scheduledHearings == 1 ? "is a hearing" : "are " + scheduledHearings + " hearings") + " still scheduled. Mark " + (scheduledHearings == 1 ? "it" : "them") + " as held or cancel " + (scheduledHearings == 1 ? "it" : "them") + " first");
        this.stage = "CONCLUDED"; this.outcome = outcome; this.concludedOn = on; this.outcomeNote = note.trim(); this.updatedAt = now;
    }

    public void reopen(String toStage, String reason, Instant now) {
        if (isOpen()) throw new IllegalStateException("This case is not concluded");
        String problem = LitigationRules.textProblem(reason, "why the case is being reopened");
        if (problem != null) throw new IllegalArgumentException(problem);
        if (toStage == null || !LitigationRules.STAGES.contains(toStage) || "CONCLUDED".equals(toStage)) throw new IllegalArgumentException("Choose the stage to reopen it at");
        this.stage = toStage; this.outcome = null; this.concludedOn = null; this.outcomeNote = null; this.updatedAt = now;
    }

    private void apply(String courtLevel, String courtName, String ourRole, String caseNumber, String opposingParty, String opposingAttorney, String opposingAttorneyContact,
                       String judge, LocalDate issuedOn, LocalDate servedOn, String notes) {
        this.courtLevel = courtLevel; this.courtName = courtName.trim(); this.ourRole = ourRole; this.caseNumber = blank(caseNumber);
        this.opposingParty = blank(opposingParty); this.opposingAttorney = blank(opposingAttorney); this.opposingAttorneyContact = blank(opposingAttorneyContact);
        this.judge = blank(judge); this.issuedOn = issuedOn; this.servedOn = servedOn; this.notes = blank(notes);
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
