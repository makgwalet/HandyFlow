package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity(name = "AuditReportSection")
@Table(name = "audit_report_sections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditReportSection {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "report_id", nullable = false) private UUID reportId;
    @Column(name = "position", nullable = false) private int position;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "body", nullable = false, columnDefinition = "TEXT") private String body = "";
    @Column(name = "auto_findings", nullable = false) private boolean autoFindings;

    public static AuditReportSection of(UUID tenantId, UUID reportId, int position, String title, String body, boolean autoFindings) {
        AuditReportSection s = new AuditReportSection();
        s.tenantId = tenantId; s.reportId = reportId; s.position = position; s.title = title; s.body = body == null ? "" : body; s.autoFindings = autoFindings;
        return s;
    }

    public void edit(String newTitle, String newBody) {
        if (newTitle == null || newTitle.isBlank()) throw new IllegalArgumentException("A section needs a title");
        this.title = newTitle.trim(); this.body = newBody == null ? "" : newBody;
    }
}
