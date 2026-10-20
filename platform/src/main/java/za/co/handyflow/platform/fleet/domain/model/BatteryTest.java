package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A resting-voltage test result. Immutable once recorded. */
@Entity
@Table(name = "fleet_battery_tests")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BatteryTest {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "battery_id", nullable = false)
    private UUID batteryId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "tested_at", nullable = false)
    private Instant testedAt;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal voltage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BatteryTestResult result;

    @Column(length = 500)
    private String notes;

    @Column(name = "tested_by")
    private UUID testedBy;

    public static BatteryTest record(TenantId tenantId, UUID batteryId, UUID vehicleId, Instant testedAt,
                                     BigDecimal voltage, BatteryTestResult result, String notes, UUID testedBy) {
        BatteryTest t = new BatteryTest();
        t.tenantId = tenantId;
        t.batteryId = batteryId;
        t.vehicleId = vehicleId;
        t.testedAt = testedAt;
        t.voltage = voltage;
        t.result = result;
        t.notes = notes;
        t.testedBy = testedBy;
        return t;
    }
}