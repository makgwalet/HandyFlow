package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One answered line of an inspection. A defect or unsafe line carries the incident it raised. */
@Entity
@Table(name = "earthmoving_inspection_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthInspectionItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "inspection_id", nullable = false)
    private UUID inspectionId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String item;

    @Column(nullable = false)
    private String outcome;

    private String comment;

    @Column(name = "incident_id")
    private UUID incidentId;

    public static EarthInspectionItem of(TenantId tenantId, UUID inspectionId, int position, InspectionRules.Answer a) {
        EarthInspectionItem x = new EarthInspectionItem();
        x.tenantId = tenantId;
        x.inspectionId = inspectionId;
        x.position = position;
        x.item = a.item();
        x.outcome = a.outcome();
        x.comment = a.comment();
        return x;
    }

    public void linkIncident(UUID incident) { this.incidentId = incident; }
}
