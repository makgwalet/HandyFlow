// security/domain/model/CheckpointLog.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_checkpoint_logs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CheckpointLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "checkpoint_id", nullable = false)
    private UUID checkpointId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String notes;

    /**
     * How the checkpoint was verified: QR | NFC | BLE | GPS_PING | MANUAL
     * Stored per-log so patrol audit reports can show method used at each checkpoint.
     */
    @Column(name = "scan_type")
    private String scanType;

    /** The patrol round this scan counted towards (V111 column); null when it was not part of a round. */
    @Column(name = "round_id")
    private UUID roundId;

    public void attachRound(UUID roundId) { this.roundId = roundId; }

    public static CheckpointLog create(TenantId tenantId, UUID checkpointId,
                                       UUID guardId, UUID shiftId,
                                       BigDecimal latitude, BigDecimal longitude,
                                       String scanType) {
        CheckpointLog log = new CheckpointLog();
        log.tenantId      = tenantId;
        log.checkpointId  = checkpointId;
        log.guardId       = guardId;
        log.shiftId       = shiftId;
        log.scannedAt     = Instant.now();
        log.latitude      = latitude;
        log.longitude     = longitude;
        log.scanType      = scanType;
        return log;
    }
}