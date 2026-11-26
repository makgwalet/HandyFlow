package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AuditRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One saved version of a checklist's items. Written once, never changed. */
@Entity(name = "TrainProvAuditTemplateVersion")
@Table(name = "trainprov_audit_template_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAuditTemplateVersion {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;
    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;
    @Column(name = "items_text", nullable = false, updatable = false)
    private String itemsText;
    @Column(name = "change_note", updatable = false)
    private String changeNote;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static TrainProvAuditTemplateVersion of(TenantId t, UUID templateId, int versionNo, String itemsText, String changeNote, UUID by) {
        String p = AuditRules.itemsProblem(itemsText);
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        TrainProvAuditTemplateVersion v = new TrainProvAuditTemplateVersion();
        v.tenantId = t.getValue();
        v.templateId = templateId;
        v.versionNo = versionNo;
        v.itemsText = String.join("\n", AuditRules.parseItems(itemsText));
        v.changeNote = changeNote == null || changeNote.isBlank() ? null : changeNote.trim();
        v.createdBy = by;
        v.createdAt = Instant.now();
        return v;
    }
}
