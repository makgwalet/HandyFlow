package za.co.handyflow.platform.security.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Keeps each phone's push token and sends short alerts to a guard. Nothing here can break the action that caused the
 * alert: sending happens after that action has committed, on its own thread, and every failure is only logged.
 * Switched off unless handyflow.guard.push.enabled=true.
 */
@Slf4j
@Service
public class GuardPushService {

    private static final Pattern EXPO_TOKEN = Pattern.compile("^Expo(nent)?PushToken\\[[A-Za-z0-9_\\-]{10,200}]$");

    private final JdbcTemplate jdbc;
    private final ExpoPushClient client;
    private final boolean enabled;
    private final Executor executor;

    @Autowired
    public GuardPushService(JdbcTemplate jdbc, ExpoPushClient client,
                            @Value("${handyflow.guard.push.enabled:false}") boolean enabled) {
        this(jdbc, client, enabled, Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "guard-push");
            t.setDaemon(true);
            return t;
        }));
    }

    GuardPushService(JdbcTemplate jdbc, ExpoPushClient client, boolean enabled, Executor executor) {
        this.jdbc = jdbc;
        this.client = client;
        this.enabled = enabled;
        this.executor = executor;
    }

    /** The phone's token now belongs to this guard. Any other token held for this phone is revoked. */
    @Transactional
    public void register(TenantId tenantId, UUID guardId, String deviceHardwareId, String expoToken) {
        if (expoToken == null || !EXPO_TOKEN.matcher(expoToken).matches()) {
            throw new HandyFlowException("That is not a valid push token.", HttpStatus.BAD_REQUEST, "INVALID_PUSH_TOKEN");
        }
        if (deviceHardwareId == null || deviceHardwareId.isBlank() || deviceHardwareId.length() > 100) {
            throw new HandyFlowException("A device id is required.", HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
        jdbc.update("UPDATE security_guard_push_tokens SET revoked_at = NOW(), updated_at = NOW() "
                + "WHERE tenant_id = ? AND device_hardware_id = ? AND expo_token <> ? AND revoked_at IS NULL",
                tenantId.getValue(), deviceHardwareId, expoToken);
        jdbc.update("INSERT INTO security_guard_push_tokens (tenant_id, guard_id, device_hardware_id, expo_token) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (tenant_id, expo_token) DO UPDATE SET guard_id = EXCLUDED.guard_id, "
                + "device_hardware_id = EXCLUDED.device_hardware_id, revoked_at = NULL, updated_at = NOW()",
                tenantId.getValue(), guardId, deviceHardwareId, expoToken);
    }

    /** On sign-out: this guard stops receiving alerts on this phone. */
    @Transactional
    public void unregister(TenantId tenantId, UUID guardId, String deviceHardwareId) {
        jdbc.update("UPDATE security_guard_push_tokens SET revoked_at = NOW(), updated_at = NOW() "
                + "WHERE tenant_id = ? AND guard_id = ? AND device_hardware_id = ? AND revoked_at IS NULL",
                tenantId.getValue(), guardId, deviceHardwareId);
    }

    /** Alert a guard on every phone they are signed in on. Never throws. Sent after the surrounding transaction commits. */
    public void notifyGuard(TenantId tenantId, UUID guardId, String title, String body, Map<String, String> data) {
        if (!enabled || guardId == null) return;
        afterCommit(() -> deliver(tenantId, guardId, title, body, data), guardId);
    }

    /**
     * Alert every site supervisor who is on duty at the site this guard is on shift at (not the guard themselves).
     * Looked up after commit, so the request is not slowed down. A guard with no open shift alerts nobody.
     */
    public void notifySiteSupervisorsOf(TenantId tenantId, UUID subjectGuardId, String title, String body, Map<String, String> data) {
        if (!enabled || subjectGuardId == null) return;
        afterCommit(() -> {
            try {
                List<UUID> supervisors = jdbc.query("""
                        SELECT DISTINCT g.id FROM security_guards g
                          JOIN security_device_sessions ds ON ds.guard_id = g.id AND ds.ended_at IS NULL
                          JOIN security_shifts s ON s.id = ds.shift_id
                         WHERE g.tenant_id = ? AND g.site_supervisor = TRUE AND g.deleted_at IS NULL AND g.id <> ?
                           AND s.site_id = (SELECT s2.site_id FROM security_device_sessions d2
                                              JOIN security_shifts s2 ON s2.id = d2.shift_id
                                             WHERE d2.guard_id = ? AND d2.ended_at IS NULL LIMIT 1)
                        """, (rs, i) -> rs.getObject(1, UUID.class), tenantId.getValue(), subjectGuardId, subjectGuardId);
                supervisors.forEach(id -> deliver(tenantId, id, title, body, data));
            } catch (Exception e) {
                log.warn("[Security] Could not alert site supervisors for guard={}: {}", subjectGuardId, e.toString());
            }
        }, subjectGuardId);
    }

    /**
     * Alert guards on duty at a site (those with an open session on a shift there): all of them, or only the site
     * supervisors, optionally leaving one guard out. Looked up after commit. Nobody on duty alerts nobody.
     */
    public void notifyOnDutyAt(TenantId tenantId, UUID siteId, boolean supervisorsOnly, UUID exclude,
                               String title, String body, Map<String, String> data) {
        if (!enabled || siteId == null) return;
        afterCommit(() -> {
            try {
                List<UUID> guards = jdbc.query("""
                        SELECT DISTINCT g.id FROM security_guards g
                          JOIN security_device_sessions ds ON ds.guard_id = g.id AND ds.ended_at IS NULL
                          JOIN security_shifts s ON s.id = ds.shift_id
                         WHERE g.tenant_id = ? AND g.deleted_at IS NULL AND s.site_id = ?
                           AND (? = FALSE OR g.site_supervisor = TRUE)
                           AND (?::uuid IS NULL OR g.id <> ?::uuid)
                        """, (rs, i) -> rs.getObject(1, UUID.class), tenantId.getValue(), siteId, supervisorsOnly, exclude, exclude);
                guards.forEach(id -> deliver(tenantId, id, title, body, data));
            } catch (Exception e) {
                log.warn("[Security] Could not alert guards on duty at site={}: {}", siteId, e.toString());
            }
        }, null);
    }

    private void afterCommit(Runnable work, UUID guardId) {
        Runnable send = () -> executor.execute(work);
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { send.run(); }
                });
            } else {
                send.run();
            }
        } catch (Exception e) {
            log.warn("[Security] Could not queue push for guard={}: {}", guardId, e.toString());
        }
    }

    void deliver(TenantId tenantId, UUID guardId, String title, String body, Map<String, String> data) {
        try {
            List<String> tokens = jdbc.queryForList("SELECT expo_token FROM security_guard_push_tokens "
                    + "WHERE tenant_id = ? AND guard_id = ? AND revoked_at IS NULL", String.class, tenantId.getValue(), guardId);
            if (tokens.isEmpty()) return;
            List<ExpoPushClient.Message> messages = tokens.stream().map(t -> new ExpoPushClient.Message(t, title, body, data)).toList();
            List<ExpoPushClient.Result> results = client.send(messages);
            for (int i = 0; i < results.size() && i < tokens.size(); i++) {
                if (results.get(i).deadToken()) {
                    jdbc.update("UPDATE security_guard_push_tokens SET revoked_at = NOW(), updated_at = NOW() "
                            + "WHERE tenant_id = ? AND expo_token = ?", tenantId.getValue(), tokens.get(i));
                }
            }
        } catch (Exception e) {
            log.warn("[Security] Push to guard={} failed: {}", guardId, e.toString());
        }
    }
}
