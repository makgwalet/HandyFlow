// security/domain/model/GuardComplaint.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A complaint about a guard and how it was handled. The rules for which step may follow which are in
 * ComplaintWorkflow; this class only records the facts of each step. Complaints are never deleted.
 */
@Entity
@Table(name = "security_guard_complaints")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardComplaint {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "complaint_number", nullable = false, length = 40)
    private String complaintNumber;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "complainant_type", nullable = false, length = 30)
    private ComplainantType complainantType;

    @Column(name = "complainant_name", length = 200)
    private String complainantName;

    @Column(name = "complainant_contact", length = 200)
    private String complainantContact;

    @Column(columnDefinition = "text")
    private String witnesses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.RECEIVED;

    @Column(name = "investigator_name", length = 200)
    private String investigatorName;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Finding finding;

    @Column(name = "finding_note", columnDefinition = "text")
    private String findingNote;

    @Column(name = "finding_by_name", length = 200)
    private String findingByName;

    @Column(name = "finding_at")
    private Instant findingAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Action action;

    @Column(name = "action_note", columnDefinition = "text")
    private String actionNote;

    @Column(name = "action_by_name", length = 200)
    private String actionByName;

    @Column(name = "action_at")
    private Instant actionAt;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "closed_by_name", length = 200)
    private String closedByName;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "withdrawn_reason", columnDefinition = "text")
    private String withdrawnReason;

    @Column(name = "hr_disciplinary_id")
    private UUID hrDisciplinaryId;

    @Column(name = "hr_referred_at")
    private Instant hrReferredAt;

    @Column(name = "hr_referred_by", length = 200)
    private String hrReferredBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static GuardComplaint log(TenantId tenantId, String number, UUID guardId, UUID siteId, LocalDate occurredOn,
                                     Category category, Severity severity, String description, ComplainantType complainantType,
                                     String complainantName, String complainantContact, String witnesses,
                                     UUID createdBy, String createdByName) {
        GuardComplaint c = new GuardComplaint();
        c.tenantId = tenantId;
        c.complaintNumber = number;
        c.guardId = guardId;
        c.createdBy = createdBy;
        c.createdByName = createdByName;
        c.createdAt = Instant.now();
        c.edit(siteId, occurredOn, category, severity, description, complainantType, complainantName, complainantContact, witnesses);
        return c;
    }

    public void edit(UUID siteId, LocalDate occurredOn, Category category, Severity severity, String description,
                     ComplainantType complainantType, String complainantName, String complainantContact, String witnesses) {
        this.siteId = siteId;
        this.occurredOn = occurredOn;
        this.category = category;
        this.severity = severity;
        this.description = description.trim();
        this.complainantType = complainantType;
        this.complainantName = blank(complainantName);
        this.complainantContact = blank(complainantContact);
        this.witnesses = blank(witnesses);
        this.updatedAt = Instant.now();
    }

    public void startInvestigation(String investigator) {
        this.status = Status.UNDER_INVESTIGATION;
        this.investigatorName = blank(investigator);
        this.updatedAt = Instant.now();
    }

    public void recordFinding(Finding finding, String note, String byName) {
        this.status = Status.FINDING_MADE;
        this.finding = finding;
        this.findingNote = blank(note);
        this.findingByName = byName;
        this.findingAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void recordAction(Action action, String note, String byName) {
        this.status = Status.ACTION_TAKEN;
        this.action = action;
        this.actionNote = blank(note);
        this.actionByName = byName;
        this.actionAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void close(String resolutionNote, String byName) {
        this.status = Status.CLOSED;
        this.resolutionNote = blank(resolutionNote);
        this.closedByName = byName;
        this.closedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void withdraw(String reason, String byName) {
        this.status = Status.WITHDRAWN;
        this.withdrawnReason = blank(reason);
        this.closedByName = byName;
        this.closedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Remembers the HR disciplinary case this complaint was referred to. Once only. */
    public void referToHr(UUID disciplinaryId, String byName) {
        this.hrDisciplinaryId = disciplinaryId;
        this.hrReferredAt = Instant.now();
        this.hrReferredBy = byName;
        this.updatedAt = Instant.now();
    }

    /** Back to investigation. The earlier finding, action and closure are cleared; the timeline event records them. */
    public void reopen() {
        this.status = Status.UNDER_INVESTIGATION;
        this.finding = null;
        this.findingNote = null;
        this.findingByName = null;
        this.findingAt = null;
        this.action = null;
        this.actionNote = null;
        this.actionByName = null;
        this.actionAt = null;
        this.resolutionNote = null;
        this.closedByName = null;
        this.closedAt = null;
        this.updatedAt = Instant.now();
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
