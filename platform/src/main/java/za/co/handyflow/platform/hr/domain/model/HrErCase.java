package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One employee relations case: a disciplinary matter, a grievance or an incapacity process. Mapped to the table that used to hold
 * disciplinary records, so every old record and id is still here (see V493). What may happen next, and what must be true first, is in
 * {@link ErRules}, {@link DisciplineRules}, {@link GrievanceRules} and {@link IncapacityRules}; this class only holds the facts.
 */
@Entity
@Table(name = "hr_er_cases")
@Getter
@NoArgsConstructor
public class HrErCase {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    @Column(name = "case_number")   String caseNumber;
    @Column(name = "case_type")     String caseType;
    String category;
    @Column(name = "employee_id")   UUID employeeId;
    String title;
    String description;
    String status = ErRules.REPORTED;
    boolean confidential;
    boolean legacy;
    @Column(name = "incident_date") LocalDate incidentDate;
    @Column(name = "reported_on")   LocalDate reportedOn;
    @Column(name = "issued_by")     UUID openedBy;
    @Column(name = "owner_user_id") UUID ownerUserId;
    @Column(name = "initiator_id")  UUID initiatorId;
    @Column(name = "chair_id")      UUID chairId;
    @Column(name = "respondent_id") UUID respondentId;

    @Column(name = "notice_issued_at") Instant noticeIssuedAt;
    @Column(name = "hearing_date")  LocalDate hearingDate;
    @Column(name = "hearing_time")  LocalTime hearingTime;
    @Column(name = "hearing_venue") String hearingVenue;
    @Column(name = "hearing_held_on") LocalDate hearingHeldOn;
    @Column(name = "hearing_notes") String hearingNotes;
    String representative;
    @Column(name = "employee_attended") Boolean employeeAttended;
    @Column(name = "employee_response") String employeeResponse;

    String outcome;
    @Column(name = "decided_on")    LocalDate decidedOn;
    @Column(name = "decision_note") String decisionNote;
    @Column(name = "decided_by")    UUID decidedBy;
    @Column(name = "warning_months") Integer warningMonths;
    @Column(name = "warning_valid_until") LocalDate warningValidUntil;
    @Column(name = "communicated_on") LocalDate communicatedOn;
    @Column(name = "dismissal_rights_explained_on") LocalDate dismissalRightsExplainedOn;
    boolean acknowledged;
    @Column(name = "acknowledged_at") Instant acknowledgedAt;

    @Column(name = "suspended_from") LocalDate suspendedFrom;
    @Column(name = "suspended_to")   LocalDate suspendedTo;
    @Column(name = "suspension_reason") String suspensionReason;
    @Column(name = "review_from")   LocalDate reviewFrom;
    @Column(name = "review_to")     LocalDate reviewTo;

    @Column(name = "appeal_lodged_on")  LocalDate appealLodgedOn;
    @Column(name = "appeal_grounds")    String appealGrounds;
    @Column(name = "appeal_decider_id") UUID appealDeciderId;
    @Column(name = "appeal_decided_on") LocalDate appealDecidedOn;
    @Column(name = "appeal_outcome")    String appealOutcome;
    @Column(name = "appeal_note")       String appealNote;

    @Column(name = "closed_on")     LocalDate closedOn;
    @Column(name = "closed_reason") String closedReason;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "updated_at")    Instant updatedAt;
    @Version long version;

    public static HrErCase open(UUID tenantId, String number, String type, String category, UUID employeeId, String title, String description,
                                LocalDate incidentDate, LocalDate reportedOn, boolean confidential, UUID openedBy, UUID ownerUserId) {
        HrErCase c = new HrErCase();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.caseNumber = number;
        c.caseType = type;
        c.category = category;
        c.employeeId = employeeId;
        c.title = title;
        c.description = description;
        c.incidentDate = incidentDate;
        c.reportedOn = reportedOn;
        c.confidential = confidential;
        c.openedBy = openedBy;
        c.ownerUserId = ownerUserId;
        c.createdAt = Instant.now();
        c.updatedAt = c.createdAt;
        return c;
    }

    private void touch() { updatedAt = Instant.now(); }

    public void moveTo(String status) { this.status = status; touch(); }
    public void setOwner(UUID userId) { this.ownerUserId = userId; touch(); }
    public void setConfidential(boolean v) { this.confidential = v; touch(); }
    public void setParties(UUID initiator, UUID chair, UUID respondent) {
        this.initiatorId = initiator;
        this.chairId = chair;
        this.respondentId = respondent;
        touch();
    }

    public void issueNotice(Instant at, LocalDate date, LocalTime time, String venue, UUID chair, UUID initiator) {
        this.noticeIssuedAt = at;
        this.hearingDate = date;
        this.hearingTime = time;
        this.hearingVenue = venue;
        if (chair != null) this.chairId = chair;
        if (initiator != null) this.initiatorId = initiator;
        this.status = ErRules.NOTICE_ISSUED;
        touch();
    }

    public void holdHearing(LocalDate heldOn, Boolean attended, String representative, String notes) {
        this.hearingHeldOn = heldOn;
        this.employeeAttended = attended;
        this.representative = representative;
        this.hearingNotes = notes;
        this.status = ErRules.HEARING_HELD;
        touch();
    }

    /** The decision: a sanction, a grievance outcome or an incapacity outcome. A warning's validity runs from the decision date. */
    public void decide(String outcome, LocalDate on, String note, UUID by, Integer warningMonths, String employeeResponse) {
        this.outcome = outcome;
        this.decidedOn = on;
        this.decisionNote = note;
        this.decidedBy = by;
        this.warningMonths = warningMonths;
        this.warningValidUntil = warningMonths == null ? null : DisciplineRules.validUntil(on, warningMonths);
        if (employeeResponse != null) this.employeeResponse = employeeResponse;
        this.status = ErRules.DECIDED;
        touch();
    }

    public void communicate(LocalDate on, boolean rightsExplained) {
        this.communicatedOn = on;
        if (rightsExplained) this.dismissalRightsExplainedOn = on;
        touch();
    }

    public void acknowledge() {
        this.acknowledged = true;
        this.acknowledgedAt = Instant.now();
        touch();
    }

    public void suspend(LocalDate from, LocalDate to, String reason) {
        this.suspendedFrom = from;
        this.suspendedTo = to;
        this.suspensionReason = reason;
        touch();
    }

    public void endSuspension(LocalDate on) { this.suspendedTo = on; touch(); }

    public void setReview(LocalDate from, LocalDate to) { this.reviewFrom = from; this.reviewTo = to; touch(); }

    public void lodgeAppeal(LocalDate on, String grounds) {
        this.appealLodgedOn = on;
        this.appealGrounds = grounds;
        this.status = ErRules.APPEALED;
        touch();
    }

    /** The appeal is decided: the case closes, and a changed outcome (and its warning) replaces the original. */
    public void decideAppeal(UUID decider, LocalDate on, String appealOutcome, String note, String newOutcome, Integer warningMonths) {
        this.appealDeciderId = decider;
        this.appealDecidedOn = on;
        this.appealOutcome = appealOutcome;
        this.appealNote = note;
        if (newOutcome != null) {
            this.outcome = newOutcome;
            this.warningMonths = warningMonths;
            this.warningValidUntil = warningMonths == null || decidedOn == null ? null : DisciplineRules.validUntil(decidedOn, warningMonths);
        }
        this.status = ErRules.CLOSED;
        this.closedOn = on;
        this.closedReason = "Appeal decided: " + appealOutcome.toLowerCase();
        touch();
    }

    public void close(LocalDate on, String reason) {
        this.status = ErRules.CLOSED;
        this.closedOn = on;
        this.closedReason = reason;
        touch();
    }

    public void withdraw(LocalDate on, String reason) {
        this.status = ErRules.WITHDRAWN;
        this.closedOn = on;
        this.closedReason = reason;
        touch();
    }
}
