package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One version of a company's checklist for one inspection type. A draft can be changed; once published it never is. */
@Entity
@Table(name = "earthmoving_checklist_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthChecklistVersion {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "inspection_type", nullable = false)
    private String inspectionType;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String status = DRAFT;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "requires_signoff", nullable = false)
    private boolean requiresSignoff;

    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static EarthChecklistVersion draft(TenantId tenantId, String type, int versionNo, boolean requiresSignoff, UUID by) {
        EarthChecklistVersion v = new EarthChecklistVersion();
        v.tenantId = tenantId;
        v.inspectionType = InspectionRules.type(type);
        v.versionNo = versionNo;
        v.requiresSignoff = requiresSignoff;
        v.createdBy = by;
        return v;
    }

    public boolean isDraft() { return DRAFT.equals(status); }

    public void change(boolean requiresSignoff, String note) {
        if (!isDraft()) throw new IllegalStateException("A published checklist is never changed: start a new version.");
        this.requiresSignoff = requiresSignoff;
        String n = note == null ? "" : note.trim();
        if (n.length() > 300) throw new IllegalArgumentException("Keep the note to 300 characters.");
        this.note = n.isEmpty() ? null : n;
    }

    public void publish(LocalDate from, UUID by) {
        if (!isDraft()) throw new IllegalStateException("This version is already published.");
        this.status = PUBLISHED;
        this.effectiveFrom = from;
        this.publishedBy = by;
        this.publishedAt = Instant.now();
    }
}
