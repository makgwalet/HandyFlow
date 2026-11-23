package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Device compliance, report only. The company's policy, the phone's own report of its posture, and the list of phones that fall short.
 * Nothing here signs a guard out or blocks a phone.
 */
@Service
@RequiredArgsConstructor
public class DeviceComplianceService {

    public record PolicyView(String minAppVersion, Integer minOsMajor, boolean requireScreenLock, boolean blockRooted, boolean asksAnything, Instant updatedAt) {}

    public record Item(UUID deviceId, String deviceName, String deviceType, UUID guardId, String guardName, String status,
                       List<String> violations, List<String> missing, String appVersion, String osVersion,
                       Boolean screenLockOn, Boolean rooted, Instant postureReportedAt) {}

    public record Report(Instant generatedAt, PolicyView policy, int activeDevices, int compliant, int nonCompliant, int unknown, List<Item> items) {}

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public PolicyView policy(TenantId tenantId) {
        return view(load(tenantId.getValue()), loadUpdatedAt(tenantId.getValue()));
    }

    @Transactional
    public PolicyView savePolicy(TenantId tenantId, UUID actor, String minAppVersion, Integer minOsMajor, Boolean requireScreenLock, Boolean blockRooted) {
        UUID t = tenantId.getValue();
        DeviceComplianceRules.Policy p = DeviceComplianceRules.cleanPolicy(minAppVersion, minOsMajor, requireScreenLock, blockRooted);
        DeviceComplianceRules.Policy before = load(t);
        jdbc.update("""
                INSERT INTO security_device_policy (tenant_id, min_app_version, min_os_major, require_screen_lock, block_rooted, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id) DO UPDATE SET min_app_version = EXCLUDED.min_app_version, min_os_major = EXCLUDED.min_os_major,
                    require_screen_lock = EXCLUDED.require_screen_lock, block_rooted = EXCLUDED.block_rooted,
                    updated_by = EXCLUDED.updated_by, updated_at = now()""",
                t, p.minAppVersion(), p.minOsMajor(), p.requireScreenLock(), p.blockRooted(), actor);
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'DEVICE_POLICY', ?, 'DEVICE_POLICY_CHANGED',
                        jsonb_build_object('before', ?::text, 'after', ?::text), now())""",
                t, actor, t, describe(before), describe(p));
        return policy(tenantId);
    }

    /** The phone's own report. A phone that belongs to another guard answers as if it did not exist, as for a heartbeat. */
    @Transactional
    public void reportPosture(TenantId tenantId, UUID guardId, String deviceHardwareId, String osVersion, Boolean screenLockOn, Boolean rooted) {
        UUID t = tenantId.getValue();
        List<Object[]> rows = jdbc.query("SELECT id, guard_id, status FROM security_devices WHERE tenant_id = ? AND device_hardware_id = ?",
                (rs, i) -> new Object[]{rs.getObject("id"), rs.getObject("guard_id"), rs.getString("status")}, t, deviceHardwareId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("SecurityDevice", deviceHardwareId);
        Object[] row = rows.get(0);
        switch (HeartbeatRules.access((UUID) row[1], "ACTIVE".equals(row[2]), guardId)) {
            case NOT_YOURS -> throw new ResourceNotFoundException("SecurityDevice", deviceHardwareId);
            case INACTIVE -> throw new HandyFlowException("This device is " + row[2] + " and is out of service", HttpStatus.FORBIDDEN, "DEVICE_INACTIVE");
            case OK -> { }
        }
        String os = DeviceComplianceRules.cleanOsVersion(osVersion);
        // A report that leaves something out never blanks what is known.
        jdbc.update("""
                UPDATE security_devices SET os_version = COALESCE(?, os_version), screen_lock_on = COALESCE(?, screen_lock_on),
                    rooted = COALESCE(?, rooted), posture_reported_at = now(), updated_at = now()
                WHERE id = ? AND tenant_id = ?""", os, screenLockOn, rooted, row[0], t);
    }

    @Transactional(readOnly = true)
    public Report report(TenantId tenantId) {
        return build(tenantId, Instant.now());
    }

    Report build(TenantId tenantId, Instant now) {
        UUID t = tenantId.getValue();
        DeviceComplianceRules.Policy p = load(t);
        record Row(UUID id, String name, String type, UUID guardId, String guardName, String app, String os, Boolean lock, Boolean rooted, Instant at) {}
        List<Row> rows = jdbc.query("""
                SELECT d.id, d.device_name, d.device_type, d.guard_id, g.first_name, g.last_name, d.app_version, d.os_version,
                       d.screen_lock_on, d.rooted, d.posture_reported_at
                FROM security_devices d LEFT JOIN security_guards g ON g.id = d.guard_id AND g.tenant_id = d.tenant_id
                WHERE d.tenant_id = ? AND d.status = 'ACTIVE'""",
                (rs, i) -> {
                    String gn = rs.getString("first_name") == null ? null : rs.getString("first_name") + " " + rs.getString("last_name");
                    Timestamp at = rs.getTimestamp("posture_reported_at");
                    return new Row((UUID) rs.getObject("id"), rs.getString("device_name"), rs.getString("device_type"), (UUID) rs.getObject("guard_id"), gn,
                            rs.getString("app_version"), rs.getString("os_version"), (Boolean) rs.getObject("screen_lock_on"), (Boolean) rs.getObject("rooted"),
                            at == null ? null : at.toInstant());
                }, t);
        int ok = 0, nonOk = 0, unk = 0;
        List<Item> items = new ArrayList<>();
        for (Row r : rows) {
            DeviceComplianceRules.Result res = DeviceComplianceRules.evaluate(p, new DeviceComplianceRules.Facts(r.app(), r.os(), r.lock(), r.rooted()));
            switch (res.status()) {
                case COMPLIANT -> ok++;
                case NON_COMPLIANT -> nonOk++;
                case UNKNOWN -> unk++;
            }
            if (res.status() != DeviceComplianceRules.Status.COMPLIANT) {
                items.add(new Item(r.id(), r.name(), r.type(), r.guardId(), r.guardName(), res.status().name(),
                        res.violations().stream().map(Enum::name).toList(), res.missing().stream().map(Enum::name).toList(),
                        r.app(), r.os(), r.lock(), r.rooted(), r.at()));
            }
        }
        // Phones known to fall short first, then the ones nobody has heard enough from, each by name.
        items.sort(Comparator.comparing((Item i) -> "NON_COMPLIANT".equals(i.status()) ? 0 : 1)
                .thenComparing(i -> i.deviceName() == null ? "" : i.deviceName().toLowerCase())
                .thenComparing(Item::deviceId));
        return new Report(now, view(p, loadUpdatedAt(t)), rows.size(), ok, nonOk, unk, List.copyOf(items));
    }

    private DeviceComplianceRules.Policy load(UUID t) {
        List<DeviceComplianceRules.Policy> found = jdbc.query(
                "SELECT min_app_version, min_os_major, require_screen_lock, block_rooted FROM security_device_policy WHERE tenant_id = ?",
                (rs, i) -> new DeviceComplianceRules.Policy(rs.getString("min_app_version"), (Integer) rs.getObject("min_os_major"),
                        rs.getBoolean("require_screen_lock"), rs.getBoolean("block_rooted")), t);
        return found.isEmpty() ? DeviceComplianceRules.Policy.none() : found.get(0);
    }

    private Instant loadUpdatedAt(UUID t) {
        List<Instant> found = jdbc.query("SELECT updated_at FROM security_device_policy WHERE tenant_id = ?",
                (rs, i) -> rs.getTimestamp("updated_at").toInstant(), t);
        return found.isEmpty() ? null : found.get(0);
    }

    private static PolicyView view(DeviceComplianceRules.Policy p, Instant updatedAt) {
        return new PolicyView(p.minAppVersion(), p.minOsMajor(), p.requireScreenLock(), p.blockRooted(), p.asksAnything(), updatedAt);
    }

    private static String describe(DeviceComplianceRules.Policy p) {
        return "app>=" + (p.minAppVersion() == null ? "-" : p.minAppVersion()) + ", os>=" + (p.minOsMajor() == null ? "-" : p.minOsMajor())
                + ", screenLock=" + p.requireScreenLock() + ", noRoot=" + p.blockRooted();
    }
}
