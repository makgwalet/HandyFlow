package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Proof of an automatic acceptance: the variance and the tolerance in force at the time, accepted by the system. Append only. */
@Entity
@Table(name = "fuel_shift_auto_accept_log")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelShiftAutoAcceptLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, updatable = false))
    private TenantId tenantId;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Column(name = "shift_id", nullable = false, updatable = false)
    private UUID shiftId;

    @Column(name = "shift_number", nullable = false, updatable = false, length = 40)
    private String shiftNumber;

    @Column(name = "attendant_name", nullable = false, updatable = false, length = 120)
    private String attendantName;

    @Column(name = "variance_amount", updatable = false, precision = 14, scale = 2)
    private BigDecimal varianceAmount;

    @Column(name = "tolerance_applied", updatable = false, precision = 10, scale = 2)
    private BigDecimal toleranceApplied;

    @Column(nullable = false, updatable = false, length = 20)
    private String actor = "SYSTEM";

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt = Instant.now();

    public static FuelShiftAutoAcceptLog of(TenantId tenant, UUID importId, FuelShift s) {
        FuelShiftAutoAcceptLog l = new FuelShiftAutoAcceptLog();
        l.tenantId = tenant; l.importId = importId; l.shiftId = s.getId(); l.shiftNumber = s.getShiftNumber(); l.attendantName = s.getAttendantName();
        l.varianceAmount = s.getVarianceAmount(); l.toleranceApplied = s.getToleranceApplied();
        return l;
    }
}
