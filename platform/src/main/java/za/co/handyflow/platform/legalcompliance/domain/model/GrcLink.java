package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One edge in the Obligation ↔ Risk ↔ Control graph. RISK_OBLIGATION: source=risk,target=obligation. RISK_CONTROL: source=risk,target=control. CONTROL_OBLIGATION: source=control,target=obligation. */
@Entity
@Table(name = "legalcompliance_grc_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrcLink extends AggregateRoot<GrcLink> {

    @Enumerated(EnumType.STRING) @Column(name = "link_type", nullable = false, length = 20) private GrcLinkType linkType;
    @Column(name = "source_id", nullable = false) private UUID sourceId;
    @Column(name = "target_id", nullable = false) private UUID targetId;
    @Column(length = 500) private String note;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static GrcLink create(TenantId tenantId, GrcLinkType type, UUID sourceId, UUID targetId, String note, UUID by, String byName) {
        GrcLink l = new GrcLink();
        l.initTenantId(tenantId);
        l.linkType = type;
        l.sourceId = sourceId;
        l.targetId = targetId;
        l.note = note;
        l.createdBy = by;
        l.createdByName = byName;
        return l;
    }
}
