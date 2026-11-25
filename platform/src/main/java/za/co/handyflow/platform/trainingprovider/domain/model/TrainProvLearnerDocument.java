package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.DocumentRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The provider's own learner document. Wording is fixed when it is issued and kept. Handed over once, voided with a reason, never deleted. */
@Entity(name = "TrainProvLearnerDocument")
@Table(name = "trainprov_learner_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnerDocument {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "document_no", nullable = false)
    private String documentNo;
    @Column(name = "doc_type", nullable = false)
    private String docType;
    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;
    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;
    @Column(name = "completion_id")
    private UUID completionId;
    @Column(name = "learner_name", nullable = false)
    private String learnerName;
    @Column(name = "programme_title", nullable = false)
    private String programmeTitle;
    @Column(name = "version_no")
    private Integer versionNo;
    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String body;
    @Column(nullable = false)
    private String disclaimer;
    @Column(nullable = false)
    private String status = DocumentRules.ISSUED;
    @Column(name = "void_reason")
    private String voidReason;
    @Column(name = "void_by")
    private UUID voidBy;
    @Column(name = "void_at")
    private Instant voidAt;
    @Column(name = "handed_over_on")
    private LocalDate handedOverOn;
    @Column(name = "handed_over_to")
    private String handedOverTo;
    @Column(name = "handed_over_method")
    private String handedOverMethod;
    @Column(name = "handed_over_by")
    private UUID handedOverBy;
    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvLearnerDocument issue(TenantId t, String no, String type, UUID enrolmentId, UUID learnerId, UUID completionId, String learnerName,
                                                 String programmeTitle, Integer versionNo, LocalDate on, LocalDate outcomeOn, UUID by) {
        TrainProvLearnerDocument d = new TrainProvLearnerDocument();
        d.tenantId = t.getValue();
        d.documentNo = no;
        d.docType = type;
        d.enrolmentId = enrolmentId;
        d.learnerId = learnerId;
        d.completionId = completionId;
        d.learnerName = learnerName;
        d.programmeTitle = programmeTitle;
        d.versionNo = versionNo;
        d.issuedOn = on;
        d.title = DocumentRules.title(type);
        d.body = DocumentRules.body(type, learnerName, programmeTitle, versionNo, outcomeOn);
        d.disclaimer = DocumentRules.DISCLAIMER;
        d.issuedBy = by;
        d.createdAt = Instant.now();
        d.updatedAt = d.createdAt;
        return d;
    }

    public boolean isHandedOver() {
        return handedOverOn != null;
    }

    public void handOver(LocalDate on, String to, String method, UUID by, LocalDate today) {
        String problem = DocumentRules.handOverProblem(status, isHandedOver(), on, issuedOn, today, to, method);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.handedOverOn = on;
        this.handedOverTo = to.trim();
        this.handedOverMethod = method;
        this.handedOverBy = by;
        this.updatedAt = Instant.now();
    }

    public void voidIt(String reason, UUID by) {
        String problem = DocumentRules.voidProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = DocumentRules.VOID;
        this.voidReason = reason.trim();
        this.voidBy = by;
        this.voidAt = Instant.now();
        this.updatedAt = this.voidAt;
    }
}
