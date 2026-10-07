// security/application/internal/PatrolOverviewService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.repository.PatrolRoundRepository;
import za.co.handyflow.platform.security.dto.PatrolDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Patrol rounds across shifts for the Patrols screens, and a supervisor's acknowledgement of a missed or partial round. */
@Service
@RequiredArgsConstructor
public class PatrolOverviewService {

    public static final Duration MAX_RANGE = Duration.ofDays(31);
    static final Set<String> STATUSES = Set.of("EXPECTED", "IN_PROGRESS", "COMPLETE", "PARTIAL", "MISSED");

    private static final String ROW_SELECT = """
            SELECT r.id, r.shift_id, r.site_id, st.name AS site_name, rt.name AS route_name,
                   g.first_name, g.last_name, r.round_number, r.status, r.expected_start_at, r.expected_end_at,
                   r.started_at, r.completed_at, r.checkpoints_expected, r.checkpoints_scanned,
                   r.off_schedule, r.off_schedule_reason, r.acknowledged_by IS NOT NULL AS acknowledged
            FROM security_patrol_rounds r
            LEFT JOIN security_shifts s ON s.id = r.shift_id
            LEFT JOIN security_guards g ON g.id = s.guard_id
            LEFT JOIN security_sites st ON st.id = r.site_id
            LEFT JOIN security_patrol_routes rt ON rt.id = r.route_id
            """;

    private final JdbcTemplate jdbc;
    private final PatrolRoundRepository roundRepository;

    @Transactional(readOnly = true)
    public List<RoundRow> list(TenantId tenantId, Instant from, Instant to, UUID siteId, String status) {
        if (from == null || to == null || !to.isAfter(from)) throw new HandyFlowException("'to' must be after 'from'", HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) throw new HandyFlowException("Ask for at most " + MAX_RANGE.toDays() + " days at a time", HttpStatus.BAD_REQUEST, "RANGE_TOO_LONG");
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) throw new HandyFlowException("Unknown status " + status, HttpStatus.BAD_REQUEST, "INVALID_STATUS");

        StringBuilder sql = new StringBuilder(ROW_SELECT).append(" WHERE r.tenant_id = ? AND r.expected_start_at >= ? AND r.expected_start_at < ?");
        List<Object> p = new ArrayList<>(List.of(tenantId.getValue(), Timestamp.from(from), Timestamp.from(to)));
        if (siteId != null) { sql.append(" AND r.site_id = ?"); p.add(siteId); }
        if (status != null && !status.isBlank()) { sql.append(" AND r.status = ?"); p.add(status); }
        sql.append(" ORDER BY r.expected_start_at DESC, r.round_number LIMIT 500");
        return jdbc.query(sql.toString(), (rs, n) -> row(rs), p.toArray());
    }

    @Transactional(readOnly = true)
    public RoundDetail detail(TenantId tenantId, UUID roundId) {
        PatrolRound entity = roundOf(tenantId, roundId);
        RoundRow row = jdbc.query(ROW_SELECT + " WHERE r.tenant_id = ? AND r.id = ?", (rs, n) -> row(rs), tenantId.getValue(), roundId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Patrol round", roundId.toString()));
        List<CheckpointRow> cps = entity.getRouteId() == null ? List.of() : jdbc.query("""
                SELECT c.id, c.name, rc.sequence, l.scanned_at, l.scan_type, g.first_name, g.last_name
                FROM security_patrol_route_checkpoints rc
                JOIN security_checkpoints c ON c.id = rc.checkpoint_id
                LEFT JOIN LATERAL (
                    SELECT scanned_at, scan_type, guard_id FROM security_checkpoint_logs
                    WHERE round_id = ? AND checkpoint_id = c.id ORDER BY scanned_at LIMIT 1
                ) l ON TRUE
                LEFT JOIN security_guards g ON g.id = l.guard_id
                WHERE rc.route_id = ? ORDER BY rc.sequence""",
                (rs, n) -> {
                    Timestamp at = rs.getTimestamp("scanned_at");
                    String first = rs.getString("first_name"), last = rs.getString("last_name");
                    return new CheckpointRow((UUID) rs.getObject("id"), rs.getString("name"), rs.getInt("sequence"),
                            at == null ? null : at.toInstant(), rs.getString("scan_type"),
                            first == null ? null : (first + " " + (last == null ? "" : last)).trim());
                }, roundId, entity.getRouteId());
        return new RoundDetail(row, entity.getAcknowledgementNote(), cps);
    }

    /** A supervisor records that they have seen a missed or partial round. It does not change the round's status. */
    @Transactional
    public RoundDetail acknowledge(TenantId tenantId, UUID roundId, UUID userId, String note) {
        PatrolRound r = roundOf(tenantId, roundId);
        if (r.getStatus() != PatrolRound.RoundStatus.MISSED && r.getStatus() != PatrolRound.RoundStatus.PARTIAL) {
            throw new HandyFlowException("Only a missed or partial round can be acknowledged", HttpStatus.CONFLICT, "ROUND_NOT_ACKNOWLEDGEABLE");
        }
        if (note == null || note.isBlank()) throw new HandyFlowException("A note is required", HttpStatus.BAD_REQUEST, "NOTE_REQUIRED");
        r.acknowledge(userId, note.trim());
        roundRepository.saveAndFlush(r);
        return detail(tenantId, roundId);
    }

    private PatrolRound roundOf(TenantId tenantId, UUID roundId) {
        PatrolRound r = roundRepository.findById(roundId).orElseThrow(() -> new ResourceNotFoundException("Patrol round", roundId.toString()));
        if (!tenantId.equals(r.getTenantId())) throw new ResourceNotFoundException("Patrol round", roundId.toString());
        return r;
    }

    static RoundRow row(ResultSet rs) throws SQLException {
        String first = rs.getString("first_name"), last = rs.getString("last_name");
        int expected = rs.getInt("checkpoints_expected");
        return new RoundRow((UUID) rs.getObject("id"), (UUID) rs.getObject("shift_id"), (UUID) rs.getObject("site_id"),
                rs.getString("site_name"), rs.getString("route_name"),
                first == null ? null : (first + " " + (last == null ? "" : last)).trim(),
                rs.getInt("round_number"), rs.getString("status"),
                at(rs, "expected_start_at"), at(rs, "expected_end_at"), at(rs, "started_at"), at(rs, "completed_at"),
                expected, rs.getInt("checkpoints_scanned"), rs.getBoolean("off_schedule"), rs.getString("off_schedule_reason"),
                rs.getBoolean("acknowledged"));
    }

    private static Instant at(ResultSet rs, String col) throws SQLException { Timestamp t = rs.getTimestamp(col); return t == null ? null : t.toInstant(); }
}
