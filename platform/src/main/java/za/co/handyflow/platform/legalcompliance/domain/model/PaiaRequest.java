package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
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
 * A PAIA access request. RECEIVED → DECIDED → CLOSED. The due date is set from the received date (see PrivacyPartyRules); it can be extended once, by a
 * further period of up to PAIA_MAX_EXTENSION_DAYS, with a reason. A decision needs a deciding person and a reason; a refusal must say on what ground.
 * A decision cannot be recorded before the request was received. After a refusal the requester may complain to the Regulator or approach a court: the
 * complaint details are recorded here but this system does not manage that process. Closing requires a decision.
 */
@Entity
@Table(name = "legalcompliance_paia_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaiaRequest extends AggregateRoot<PaiaRequest> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "requester_name", nullable = false, length = 255) private String requesterName;
    @Column(name = "requester_contact", length = 255) private String requesterContact;
    @Column(name = "on_behalf_of", length = 255) private String onBehalfOf;
    @Column(name = "record_description", nullable = false, columnDefinition = "TEXT") private String recordDescription;
    @Column(name = "received_date", nullable = false) private LocalDate receivedDate;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "extended_until") private LocalDate extendedUntil;
    @Column(name = "extension_reason", columnDefinition = "TEXT") private String extensionReason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private PaiaStatus status = PaiaStatus.RECEIVED;
    @Column(name = "assigned_to_name", length = 255) private String assignedToName;
    @Column(name = "fee_amount") private BigDecimal feeAmount;
    @Column(name = "fee_paid_on") private LocalDate feePaidOn;
    @Enumerated(EnumType.STRING) @Column(length = 20) private PaiaDecision decision;
    @Column(name = "decision_date") private LocalDate decisionDate;
    @Column(name = "decision_reason", columnDefinition = "TEXT") private String decisionReason;
    @Column(name = "decided_by_name", length = 255) private String decidedByName;
    @Column(name = "complaint_lodged_on") private LocalDate complaintLodgedOn;
    @Column(name = "complaint_reference", length = 100) private String complaintReference;
    @Column(name = "complaint_outcome", columnDefinition = "TEXT") private String complaintOutcome;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "created_by") private UUID createdBy;

    public static PaiaRequest receive(TenantId tenantId, String reference, String requesterName, String contact, String onBehalfOf, String recordDescription, LocalDate received,
                                      LocalDate dueDate, String assignedTo, UUID by) {
        PaiaRequest r = new PaiaRequest();
        r.initTenantId(tenantId);
        r.reference = reference;
        r.createdBy = by;
        r.dueDate = dueDate;
        r.apply(requesterName, contact, onBehalfOf, recordDescription, received, assignedTo);
        return r;
    }

    public void update(String requesterName, String contact, String onBehalfOf, String recordDescription, LocalDate received, String assignedTo) {
        if (status != PaiaStatus.RECEIVED) throw new IllegalStateException("Only a request still awaiting a decision can be edited — current: " + status);
        apply(requesterName, contact, onBehalfOf, recordDescription, received, assignedTo);
    }

    private void apply(String requesterName, String contact, String onBehalfOf, String recordDescription, LocalDate received, String assignedTo) {
        if (requesterName == null || requesterName.isBlank()) throw new IllegalArgumentException("The requester's name is required");
        if (recordDescription == null || recordDescription.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe the records requested (at least " + MIN_TEXT + " characters)");
        if (received == null) throw new IllegalArgumentException("The date the request was received is required");
        this.requesterName = requesterName.trim();
        this.requesterContact = blankToNull(contact);
        this.onBehalfOf = blankToNull(onBehalfOf);
        this.recordDescription = recordDescription.trim();
        this.receivedDate = received;
        this.assignedToName = blankToNull(assignedTo);
    }

    /** Moves the due date once, to no later than maxAllowed. */
    public void extend(LocalDate until, String reason, LocalDate maxAllowed) {
        if (status != PaiaStatus.RECEIVED) throw new IllegalStateException("Only a request awaiting a decision can be extended — current: " + status);
        if (extendedUntil != null) throw new IllegalStateException("This request has already been extended once");
        if (until == null || !until.isAfter(dueDate)) throw new IllegalArgumentException("The extended date must be after the current due date");
        if (until.isAfter(maxAllowed)) throw new IllegalArgumentException("The extension cannot go beyond " + maxAllowed);
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Give the reason for the extension (at least " + MIN_TEXT + " characters)");
        this.extendedUntil = until;
        this.extensionReason = reason.trim();
    }

    public void recordFee(BigDecimal amount, LocalDate paidOn) {
        if (status == PaiaStatus.CLOSED) throw new IllegalStateException("A closed request cannot be changed");
        if (amount != null && amount.signum() < 0) throw new IllegalArgumentException("A fee cannot be negative");
        if (paidOn != null && paidOn.isBefore(receivedDate)) throw new IllegalArgumentException("A fee cannot be paid before the request was received");
        this.feeAmount = amount;
        this.feePaidOn = paidOn;
    }

    public void decide(PaiaDecision decision, LocalDate date, String reason, String decidedBy) {
        if (status != PaiaStatus.RECEIVED) throw new IllegalStateException("Only a request awaiting a decision can be decided — current: " + status);
        if (decision == null) throw new IllegalArgumentException("A decision is required");
        if (date == null) throw new IllegalArgumentException("The decision date is required");
        if (date.isBefore(receivedDate)) throw new IllegalArgumentException("A decision cannot pre-date the request");
        if (decidedBy == null || decidedBy.isBlank()) throw new IllegalArgumentException("The person who decided is required");
        if (reason == null || reason.trim().length() < MIN_TEXT)
            throw new IllegalArgumentException((decision == PaiaDecision.GRANTED ? "Record the basis for the decision" : "State the ground for refusing, partly refusing or transferring") + " (at least " + MIN_TEXT + " characters)");
        this.decision = decision;
        this.decisionDate = date;
        this.decisionReason = reason.trim();
        this.decidedByName = decidedBy.trim();
        this.status = PaiaStatus.DECIDED;
    }

    public void recordComplaint(LocalDate lodgedOn, String reference, String outcome) {
        if (status == PaiaStatus.RECEIVED) throw new IllegalStateException("A complaint can only be recorded after a decision");
        if (decision == PaiaDecision.GRANTED) throw new IllegalStateException("A request that was granted in full has nothing to complain about");
        if (lodgedOn == null) throw new IllegalArgumentException("The date the complaint was lodged is required");
        if (lodgedOn.isBefore(decisionDate)) throw new IllegalArgumentException("A complaint cannot pre-date the decision");
        this.complaintLodgedOn = lodgedOn;
        this.complaintReference = blankToNull(reference);
        this.complaintOutcome = blankToNull(outcome);
    }

    public void close() {
        if (status != PaiaStatus.DECIDED) throw new IllegalStateException("Only a decided request can be closed — current: " + status);
        this.status = PaiaStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
