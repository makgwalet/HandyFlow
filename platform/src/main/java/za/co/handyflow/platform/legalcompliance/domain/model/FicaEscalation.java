package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * An internal escalation of unusual or suspicious activity. RAISED → UNDER_REVIEW → DECIDED (REPORTED / NOT_REPORTED).
 * The decider must not be the person who raised it (unconditional) and must give a reason either way. The platform does not file any report with an
 * authority: where the decision is REPORTED, the external reference is whatever the organisation received when it reported through the proper channel.
 * Access is restricted to FICA managers on purpose: telling the subject of an escalation about it can be an offence.
 */
@Entity
@Table(name = "legalcompliance_fica_escalations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FicaEscalation extends AggregateRoot<FicaEscalation> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "party_id") private UUID partyId;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "raised_by_id") private UUID raisedById;
    @Column(name = "raised_by_name", nullable = false, length = 255) private String raisedByName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private EscalationStatus status = EscalationStatus.RAISED;
    @Enumerated(EnumType.STRING) @Column(length = 15) private EscalationDecision decision;
    @Column(name = "decision_note", columnDefinition = "TEXT") private String decisionNote;
    @Column(name = "decided_by_id") private UUID decidedById;
    @Column(name = "decided_by_name", length = 255) private String decidedByName;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "external_reference", length = 100) private String externalReference;

    public static FicaEscalation raise(TenantId tenantId, String reference, UUID partyId, String description, UUID by, String byName) {
        if (description == null || description.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe what was observed (at least " + MIN_TEXT + " characters)");
        if (byName == null || byName.isBlank()) throw new IllegalArgumentException("Who raised it is required");
        FicaEscalation e = new FicaEscalation();
        e.initTenantId(tenantId);
        e.reference = reference;
        e.partyId = partyId;
        e.description = description.trim();
        e.raisedById = by;
        e.raisedByName = byName.trim();
        return e;
    }

    public void startReview() {
        if (status != EscalationStatus.RAISED) throw new IllegalStateException("Only a raised escalation can go under review — current: " + status);
        this.status = EscalationStatus.UNDER_REVIEW;
    }

    public void decide(EscalationDecision decision, String note, String externalReference, UUID by, String byName) {
        if (status != EscalationStatus.UNDER_REVIEW) throw new IllegalStateException("Only an escalation under review can be decided — current: " + status);
        if (decision == null) throw new IllegalArgumentException("A decision is required");
        if (note == null || note.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Record the reasons for the decision (at least " + MIN_TEXT + " characters)");
        if (byName == null || byName.isBlank()) throw new IllegalArgumentException("The decider is required");
        if (by != null && by.equals(raisedById)) throw new IllegalStateException("Segregation of duties: you raised this escalation, so you cannot decide it");
        this.decision = decision;
        this.decisionNote = note.trim();
        this.externalReference = decision == EscalationDecision.REPORTED && externalReference != null && !externalReference.isBlank() ? externalReference.trim() : null;
        this.decidedById = by;
        this.decidedByName = byName.trim();
        this.decidedAt = Instant.now();
        this.status = EscalationStatus.DECIDED;
    }
}
