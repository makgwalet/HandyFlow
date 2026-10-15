package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One pre-use inspection of one machine. Never edited after it is saved. */
@Entity
@Table(name = "earthmoving_inspections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthInspection {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "inspector_user_id")
    private UUID inspectorUserId;

    @Column(name = "meter_reading", precision = 10, scale = 1)
    private BigDecimal meterReading;

    @Column(nullable = false)
    private String result;

    /** PRE_USE, HANDOVER, RETURN, MAINTENANCE or SAFETY. */
    @Column(name = "inspection_type", nullable = false)
    private String inspectionType = InspectionRules.PRE_USE;

    private String notes;

    /** The checklist version this was answered against; empty when the built-in walk-round was used. */
    @Column(name = "template_version_id")
    private UUID templateVersionId;

    @Column(name = "template_version_no")
    private Integer templateVersionNo;

    @Column(name = "sign_off_name")
    private String signOffName;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    /** Records which checklist version was used and who signed it off. */
    public void stamp(UUID versionId, Integer versionNo, String signOff) {
        this.templateVersionId = versionId;
        this.templateVersionNo = versionNo;
        this.signOffName = signOff == null || signOff.isBlank() ? null : signOff.trim();
    }

    public static EarthInspection create(TenantId tenantId, UUID assetId, UUID inspector, BigDecimal meterReading, String result, String notes) {
        return create(tenantId, assetId, inspector, meterReading, result, notes, InspectionRules.PRE_USE);
    }

    public static EarthInspection create(TenantId tenantId, UUID assetId, UUID inspector, BigDecimal meterReading, String result, String notes, String type) {
        if (meterReading != null && meterReading.signum() < 0) throw new IllegalArgumentException("A meter reading cannot be negative.");
        EarthInspection i = new EarthInspection();
        i.tenantId = tenantId;
        i.assetId = assetId;
        i.inspectorUserId = inspector;
        i.meterReading = meterReading;
        i.result = result;
        i.inspectionType = InspectionRules.type(type);
        i.notes = notes == null || notes.isBlank() ? null : notes.trim();
        i.performedAt = Instant.now();
        return i;
    }
}
