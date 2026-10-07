// security/application/internal/CheckpointAdminService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.repository.CheckpointRepository;
import za.co.handyflow.platform.security.dto.CheckpointAdminDtos.Row;
import za.co.handyflow.platform.security.dto.CheckpointAdminDtos.UpdateRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** All checkpoints across sites with their 30-day scan counts, and edits to a checkpoint's details and active flag. Switching one off or on also adjusts the open patrol rounds that use it. */
@Service
@RequiredArgsConstructor
public class CheckpointAdminService {

    private final JdbcTemplate jdbc;
    private final CheckpointRepository checkpoints;

    @Transactional(readOnly = true)
    public List<Row> list(TenantId tenantId, UUID siteId, boolean includeInactive) { return list(tenantId, siteId, includeInactive, Instant.now()); }

    @Transactional(readOnly = true)
    List<Row> list(TenantId tenantId, UUID siteId, boolean includeInactive, Instant now) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.site_id, s.name AS site_name, c.name, c.description, c.active,
                       c.nfc_tag_uid IS NOT NULL AS has_nfc, c.ble_beacon_id IS NOT NULL AS has_ble, s.require_signed_qr,
                       (SELECT COUNT(*) FROM security_checkpoint_logs l WHERE l.checkpoint_id = c.id AND l.tenant_id = c.tenant_id AND l.scanned_at >= ?) AS scans30,
                       (SELECT MAX(l.scanned_at) FROM security_checkpoint_logs l WHERE l.checkpoint_id = c.id AND l.tenant_id = c.tenant_id) AS last_scan,
                       (SELECT COUNT(*) FROM security_patrol_route_checkpoints rc JOIN security_patrol_routes r ON r.id = rc.route_id
                         WHERE rc.checkpoint_id = c.id AND r.active = TRUE) AS routes
                FROM security_checkpoints c JOIN security_sites s ON s.id = c.site_id
                WHERE c.tenant_id = ?""");
        List<Object> p = new ArrayList<>(List.of(Timestamp.from(now.minus(Duration.ofDays(30))), tenantId.getValue()));
        if (siteId != null) { sql.append(" AND c.site_id = ?"); p.add(siteId); }
        if (!includeInactive) sql.append(" AND c.active = TRUE");
        sql.append(" ORDER BY s.name, c.sort_order, c.name");
        return jdbc.query(sql.toString(), (rs, n) -> {
            Timestamp last = rs.getTimestamp("last_scan");
            return new Row((UUID) rs.getObject("id"), (UUID) rs.getObject("site_id"), rs.getString("site_name"), rs.getString("name"),
                    rs.getString("description"), rs.getBoolean("active"), rs.getBoolean("has_nfc"), rs.getBoolean("has_ble"),
                    rs.getBoolean("require_signed_qr"), rs.getInt("scans30"), last == null ? null : last.toInstant(), rs.getInt("routes"));
        }, p.toArray());
    }

    @Transactional
    public Row update(TenantId tenantId, UUID id, UpdateRequest req) {
        Checkpoint c = checkpoints.findById(id).filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint", id.toString()));
        if (req.name() == null || req.name().isBlank()) throw new HandyFlowException("A checkpoint needs a name", HttpStatus.BAD_REQUEST, "NAME_REQUIRED");
        // null leaves the identifier as it is, a blank value clears it, anything else replaces it. The screen never
        // receives the identifiers, so "leave as it is" must be expressible without knowing them.
        String nfc = req.nfcTagUid() == null ? c.getNfcTagUid() : blankToNull(req.nfcTagUid());
        String ble = req.bleBeaconId() == null ? c.getBleBeaconId() : blankToNull(req.bleBeaconId());
        if (req.active()) { // identifiers only have to be unique among active checkpoints, which is what the scan lookup uses
            if (nfc != null) checkpoints.findByNfcTagUid(tenantId, nfc).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new HandyFlowException("That NFC tag is already used by " + o.getName(), HttpStatus.CONFLICT, "NFC_IN_USE"); });
            if (ble != null) checkpoints.findByBleBeaconId(tenantId, ble).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new HandyFlowException("That Bluetooth beacon is already used by " + o.getName(), HttpStatus.CONFLICT, "BLE_IN_USE"); });
        }
        boolean wasActive = c.isActive();
        c.updateDetails(req.name(), req.description(), nfc, ble, req.active());
        checkpoints.saveAndFlush(c);
        if (wasActive != req.active()) adjustOpenRounds(tenantId, id, req.active() ? 1 : -1);
        return list(tenantId, c.getSite().getId(), true).stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint", id.toString()));
    }

    /**
     * Keeps patrol rounds that are still open honest when a checkpoint is switched off or back on. A round needs one scan
     * per checkpoint on its route; a switched-off checkpoint cannot be scanned, so without this the round could never
     * complete. Only open rounds (expected or in progress) whose route includes the checkpoint, and that have not already
     * scanned it, change by one. Switching it back on reverses the same rounds. A round that now has every remaining
     * scan is completed.
     */
    void adjustOpenRounds(TenantId tenantId, UUID checkpointId, int delta) {
        jdbc.update("""
                UPDATE security_patrol_rounds r
                   SET checkpoints_expected = GREATEST(0, r.checkpoints_expected + ?), updated_at = NOW()
                 WHERE r.tenant_id = ? AND r.status IN ('EXPECTED', 'IN_PROGRESS')
                   AND r.route_id IN (SELECT rc.route_id FROM security_patrol_route_checkpoints rc WHERE rc.checkpoint_id = ?)
                   AND NOT EXISTS (SELECT 1 FROM security_checkpoint_logs l WHERE l.round_id = r.id AND l.checkpoint_id = ?)""",
                delta, tenantId.getValue(), checkpointId, checkpointId);
        if (delta < 0) {
            jdbc.update("""
                    UPDATE security_patrol_rounds r
                       SET status = 'COMPLETE', completed_at = NOW(), updated_at = NOW()
                     WHERE r.tenant_id = ? AND r.status = 'IN_PROGRESS'
                       AND r.checkpoints_expected > 0 AND r.checkpoints_scanned >= r.checkpoints_expected
                       AND r.route_id IN (SELECT rc.route_id FROM security_patrol_route_checkpoints rc WHERE rc.checkpoint_id = ?)""",
                    tenantId.getValue(), checkpointId);
        }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
