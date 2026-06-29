// security/application/internal/ScanLogService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.dto.ScanLogResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * ScanLogService — reads checkpoint scan logs for display on the LiveMapTab.
 *
 * WHY JdbcTemplate instead of CheckpointLogRepository.findByShift?
 * The repository returns CheckpointLog entities — domain objects with no
 * checkpoint name.  Resolving the name would require a second query per log row.
 * A single JDBC JOIN query gives us the name in one shot, same pattern as
 * IncidentService.getIncidents() and BookingsService.getBookings().
 *
 * The LiveMapTab reads the last element of the returned list to show
 * "Last checkpoint: {checkpointName} · {scannedAt}".
 */
@Service
@RequiredArgsConstructor
public class ScanLogService {

    private final JdbcTemplate    jdbc;
    private final ShiftRepository shiftRepository;

    @Transactional(readOnly = true)
    public List<ScanLogResponse> getScansForShift(TenantId tenantId, UUID shiftId) {
        // Validate shift belongs to this tenant
        shiftRepository.findActiveById(tenantId, shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", shiftId.toString()));

        String sql = """
                SELECT
                    l.id, l.checkpoint_id, c.name AS checkpoint_name,
                    l.guard_id, l.shift_id, l.scanned_at,
                    l.latitude, l.longitude, l.scan_type
                FROM security_checkpoint_logs l
                LEFT JOIN security_checkpoints c ON c.id = l.checkpoint_id
                WHERE l.shift_id = ?
                ORDER BY l.scanned_at ASC
                """;

        return jdbc.query(sql, (rs, i) -> {
            Timestamp ts = rs.getTimestamp("scanned_at");
            String cpId   = rs.getString("checkpoint_id");
            String gId    = rs.getString("guard_id");
            String shId   = rs.getString("shift_id");
            return new ScanLogResponse(
                    UUID.fromString(rs.getString("id")),
                    cpId  != null ? UUID.fromString(cpId)  : null,
                    rs.getString("checkpoint_name"),
                    gId   != null ? UUID.fromString(gId)   : null,
                    shId  != null ? UUID.fromString(shId)  : null,
                    ts    != null ? ts.toInstant()          : null,
                    rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude"),
                    rs.getString("scan_type")
            );
        }, shiftId);
    }
}
