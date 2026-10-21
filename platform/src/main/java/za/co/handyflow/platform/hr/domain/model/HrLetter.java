package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One letter to one employee. It is a draft until HR issues it; issuing makes the PDF and fixes the wording. After that only the
 * employee's acknowledgement and a later voiding can be added (the database refuses anything else, see V494).
 */
@Entity
@Table(name = "hr_letters")
@Getter
@NoArgsConstructor
public class HrLetter {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    String number;
    @Column(name = "template_id") UUID templateId;
    @Column(name = "template_code") String templateCode;
    @Column(name = "template_name") String templateName;
    @Column(name = "template_revision") int templateRevision;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "case_id") UUID caseId;
    String subject;
    String body;
    String status = LetterRules.DRAFT;
    @Column(name = "requires_ack") boolean requiresAck;
    @Column(name = "created_by") UUID createdBy;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "issued_on") LocalDate issuedOn;
    @Column(name = "issued_at") Instant issuedAt;
    @Column(name = "issued_by") UUID issuedBy;
    @Column(name = "file_key") String fileKey;
    @Column(name = "file_name") String fileName;
    @Column(name = "file_sha256") String fileSha256;
    @Column(name = "acknowledged_at") Instant acknowledgedAt;
    @Column(name = "acknowledged_by") UUID acknowledgedBy;
    @Column(name = "voided_at") Instant voidedAt;
    @Column(name = "voided_by") UUID voidedBy;
    @Column(name = "void_reason") String voidReason;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static HrLetter draft(UUID tenantId, String number, HrLetterTemplate t, UUID employeeId, UUID caseId, String subject, String body, UUID by) {
        HrLetter l = new HrLetter();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.number = number;
        l.templateId = t.getId();
        l.templateCode = t.getCode();
        l.templateName = t.getName();
        l.templateRevision = t.getRevision();
        l.employeeId = employeeId;
        l.caseId = caseId;
        l.subject = subject;
        l.body = body;
        l.requiresAck = t.isRequiresAck();
        l.createdBy = by;
        l.createdAt = Instant.now();
        l.updatedAt = l.createdAt;
        return l;
    }

    public void edit(String subject, String body) {
        this.subject = subject.trim();
        this.body = body;
        this.updatedAt = Instant.now();
    }

    public void issue(LocalDate on, UUID by, String fileKey, String fileName, String sha256) {
        this.status = LetterRules.ISSUED;
        this.issuedOn = on;
        this.issuedAt = Instant.now();
        this.issuedBy = by;
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.fileSha256 = sha256;
        this.updatedAt = this.issuedAt;
    }

    public void voidIt(UUID by, String reason) {
        this.status = LetterRules.VOID;
        this.voidedAt = Instant.now();
        this.voidedBy = by;
        this.voidReason = reason.trim();
        this.updatedAt = this.voidedAt;
    }

    public void acknowledge(UUID by) {
        this.acknowledgedAt = Instant.now();
        this.acknowledgedBy = by;
        this.updatedAt = this.acknowledgedAt;
    }
}
