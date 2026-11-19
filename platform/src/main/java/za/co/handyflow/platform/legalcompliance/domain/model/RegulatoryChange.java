package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One regulatory change event for one tenant-owned instrument: a new version published, or the current one repealed. */
@Entity
@Table(name = "legalcompliance_regulatory_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryChange extends AggregateRoot<RegulatoryChange> {

    @Column(name = "instrument_id", nullable = false) private UUID instrumentId;
    @Column(name = "instrument_title", nullable = false, length = 255) private String instrumentTitle;
    @Column(name = "from_version_id") private UUID fromVersionId;
    @Column(name = "to_version_id") private UUID toVersionId;
    @Column(name = "from_label", length = 100) private String fromLabel;
    @Column(name = "to_label", length = 100) private String toLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_kind", nullable = false, length = 20)
    private ChangeKind changeKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChangeStatus status = ChangeStatus.OPEN;

    @Column(columnDefinition = "TEXT") private String summary;
    @Column(name = "added_count", nullable = false) private int addedCount;
    @Column(name = "changed_count", nullable = false) private int changedCount;
    @Column(name = "removed_count", nullable = false) private int removedCount;
    @Column(name = "carried_count", nullable = false) private int carriedCount;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;
    @Column(name = "closed_at") private Instant closedAt;

    public static RegulatoryChange create(TenantId tenantId, UUID instrumentId, String instrumentTitle, UUID fromVersionId,
                                          String fromLabel, UUID toVersionId, String toLabel, ChangeKind kind, String summary,
                                          int added, int changed, int removed, int carried, UUID by, String byName) {
        RegulatoryChange c = new RegulatoryChange();
        c.initTenantId(tenantId);
        c.instrumentId = instrumentId;
        c.instrumentTitle = instrumentTitle;
        c.fromVersionId = fromVersionId;
        c.fromLabel = fromLabel;
        c.toVersionId = toVersionId;
        c.toLabel = toLabel;
        c.changeKind = kind;
        c.summary = summary;
        c.addedCount = added;
        c.changedCount = changed;
        c.removedCount = removed;
        c.carriedCount = carried;
        c.createdBy = by;
        c.createdByName = byName;
        return c;
    }

    public void close() {
        if (status == ChangeStatus.CLOSED) return;
        status = ChangeStatus.CLOSED;
        closedAt = Instant.now();
    }

    public boolean isOpen() { return status == ChangeStatus.OPEN; }
}
