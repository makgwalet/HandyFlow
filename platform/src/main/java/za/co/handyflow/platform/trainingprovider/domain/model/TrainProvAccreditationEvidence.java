package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AuthorityFrameworkRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A letter, certificate or register entry behind an accreditation. Pending until someone checks it. Never deleted: it is verified, rejected or withdrawn with a reason. */
@Entity(name = "TrainProvAccreditationEvidence")
@Table(name = "trainprov_accreditation_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAccreditationEvidence {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "accreditation_id", nullable = false)
    private UUID accreditationId;
    @Column(nullable = false)
    private String kind;
    @Column(nullable = false)
    private String description;
    private String reference;
    @Column(name = "document_date")
    private LocalDate documentDate;
    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;
    @Column(nullable = false)
    private String status = "PENDING";
    @Column(name = "added_by", nullable = false)
    private UUID addedBy;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_note")
    private String decisionNote;
    @Version
    private Long version;

    public static TrainProvAccreditationEvidence add(TenantId t, UUID accreditationId, String kind, String description, String reference,
                                                     LocalDate documentDate, LocalDate receivedOn, UUID by, LocalDate today) {
        String p = AuthorityFrameworkRules.evidenceProblem(kind, description, receivedOn, documentDate, today);
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        TrainProvAccreditationEvidence e = new TrainProvAccreditationEvidence();
        e.tenantId = t.getValue();
        e.accreditationId = accreditationId;
        e.kind = kind;
        e.description = description.trim();
        e.reference = reference == null || reference.isBlank() ? null : reference.trim();
        e.documentDate = documentDate;
        e.receivedOn = receivedOn;
        e.addedBy = by;
        e.addedAt = Instant.now();
        return e;
    }

    public void decide(String to, String note, UUID by) {
        String p = AuthorityFrameworkRules.evidenceDecisionProblem(status, to, addedBy, by, note);
        if (p != null) {
            throw new IllegalStateException(p);
        }
        this.status = to;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
    }
}
