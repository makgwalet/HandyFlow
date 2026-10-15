package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A reviewer's comment on a fact in a draft report, and the audit team's answer to it. Both stay on record. */
@Entity(name = "AuditReportFactItem")
@Table(name = "audit_report_fact_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditReportFactItem {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "report_id", nullable = false) private UUID reportId;
    @Column(name = "section_position") private Integer sectionPosition;
    @Column(name = "comment", nullable = false, columnDefinition = "TEXT") private String comment;
    @Column(name = "raised_by", nullable = false) private UUID raisedBy;
    @Column(name = "raised_by_name", nullable = false) private String raisedByName;
    @Column(name = "raised_at", nullable = false) private Instant raisedAt = Instant.now();
    @Column(name = "status", nullable = false) private String status = "OPEN";
    @Column(name = "response", columnDefinition = "TEXT") private String response;
    @Column(name = "responded_by") private UUID respondedBy;
    @Column(name = "responded_by_name") private String respondedByName;
    @Column(name = "responded_at") private Instant respondedAt;

    private static String text(String t, String what) {
        if (t == null || t.trim().length() < AuditReport.MIN_TEXT) throw new IllegalArgumentException(what + " needs at least " + AuditReport.MIN_TEXT + " characters");
        return t.trim();
    }

    public static AuditReportFactItem raise(UUID tenantId, UUID reportId, Integer section, String comment, UUID by, String byName) {
        AuditReportFactItem i = new AuditReportFactItem();
        i.tenantId = tenantId; i.reportId = reportId; i.sectionPosition = section; i.comment = text(comment, "The comment");
        i.raisedBy = by; i.raisedByName = byName;
        return i;
    }

    public void respond(String decision, String response, UUID by, String byName) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("This comment has already been answered");
        String d = decision == null ? "" : decision.toUpperCase();
        if (!d.equals("ACCEPTED") && !d.equals("DECLINED")) throw new IllegalArgumentException("Decision must be ACCEPTED or DECLINED");
        this.response = text(response, "The response");
        this.status = d; this.respondedBy = by; this.respondedByName = byName; this.respondedAt = Instant.now();
    }
}
