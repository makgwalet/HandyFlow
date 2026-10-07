package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** What was last typed and ticked on a tender's package screen. One per tender; opaque JSON owned by the screen. */
@Entity
@Table(name = "tender_package_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderPackageDraft {

    /** About 200 KB: a long cover letter and company profile fit many times over; anything bigger is not a draft. */
    public static final int MAX_CHARS = 200_000;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_by_name")
    private String updatedByName;

    public static TenderPackageDraft create(TenantId tenantId, UUID tenderId, String data, UUID by, String byName) {
        TenderPackageDraft d = new TenderPackageDraft();
        d.tenantId = tenantId;
        d.tenderId = tenderId;
        d.replace(data, by, byName);
        return d;
    }

    public void replace(String data, UUID by, String byName) {
        if (data == null || data.isBlank()) throw new IllegalArgumentException("The draft is empty.");
        if (data.length() > MAX_CHARS) throw new IllegalArgumentException("The draft is too large to save.");
        this.data = data;
        this.updatedAt = Instant.now();
        this.updatedBy = by;
        this.updatedByName = byName;
    }
}
