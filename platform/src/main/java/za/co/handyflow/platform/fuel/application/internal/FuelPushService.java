package za.co.handyflow.platform.fuel.application.internal;

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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Keeps each phone's push token and sends short alerts to Fuel app users. Nothing here can break the action that caused
 * the alert: sending happens after that action has committed, on its own thread, and every failure is only logged.
 * An alert carries no figures a lock screen should not show: a title, one short line, and the screen to open.
 * Switched off unless handyflow.fuel.push.enabled=true.
 */
@Slf4j
@Service
public class FuelPushService {

    private static final Pattern EXPO_TOKEN = Pattern.compile("^Expo(nent)?PushToken\\[[A-Za-z0-9_\\-]{10,200}]$");

    private final JdbcTemplate jdbc;
    private final FuelExpoPushClient client;
    private final boolean enabled;
    private final Executor executor;

    @Autowired
    public FuelPushService(JdbcTemplate jdbc, FuelExpoPushClient client, @Value("${handyflow.fuel.push.enabled:false}") boolean enabled) {
        this(jdbc, client, enabled, Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "fuel-push");
            t.setDaemon(true);
            return t;
        }));
    }

    FuelPushService(JdbcTemplate jdbc, FuelExpoPushClient client, boolean enabled, Executor executor) {
        this.jdbc = jdbc;
        this.client = client;
        this.enabled = enabled;
        this.executor = executor;
    }

    /** The phone's token now belongs to this user. Any other token held for this phone is revoked. */
    @Transactional
    public void register(TenantId tenantId, UUID userId, String deviceId, String expoToken) {
        if (expoToken == null || !EXPO_TOKEN.matcher(expoToken).matches()) {
            throw new HandyFlowException("That is not a valid push token.", HttpStatus.BAD_REQUEST, "INVALID_PUSH_TOKEN");
        }
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > 100) {
            throw new HandyFlowException("A device id is required.", HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
        jdbc.update("UPDATE fuel_push_tokens SET revoked_at = NOW(), updated_at = NOW() "
                + "WHERE tenant_id = ? AND device_id = ? AND expo_token <> ? AND revoked_at IS NULL", tenantId.getValue(), deviceId, expoToken);
        jdbc.update("INSERT INTO fuel_push_tokens (tenant_id, user_id, device_id, expo_token) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (tenant_id, expo_token) DO UPDATE SET user_id = EXCLUDED.user_id, device_id = EXCLUDED.device_id, "
                + "revoked_at = NULL, updated_at = NOW()", tenantId.getValue(), userId, deviceId, expoToken);
    }

    /** On sign-out: this user stops receiving alerts on this phone. */
    @Transactional
    public void unregister(TenantId tenantId, UUID userId, String deviceId) {
        jdbc.update("UPDATE fuel_push_tokens SET revoked_at = NOW(), updated_at = NOW() "
                + "WHERE tenant_id = ? AND user_id = ? AND device_id = ? AND revoked_at IS NULL", tenantId.getValue(), userId, deviceId);
    }

    /** Alert one user on every phone they are signed in on. Never throws. Sent after the surrounding transaction commits. */
    public void notifyUser(TenantId tenantId, UUID userId, String title, String body, Map<String, String> data) {
        if (userId == null) return;
        notifyUsers(tenantId, List.of(userId), title, body, data);
    }

    public void notifyUsers(TenantId tenantId, Collection<UUID> userIds, String title, String body, Map<String, String> data) {
        if (!enabled || userIds == null || userIds.isEmpty()) return;
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(userIds));
        afterCommit(() -> ids.forEach(id -> deliver(tenantId, id, title, body, data)));
    }

    private void afterCommit(Runnable work) {
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
            log.warn("[Fuel] Could not queue push: {}", e.toString());
        }
    }

    void deliver(TenantId tenantId, UUID userId, String title, String body, Map<String, String> data) {
        try {
            List<String> tokens = jdbc.queryForList("SELECT expo_token FROM fuel_push_tokens WHERE tenant_id = ? AND user_id = ? AND revoked_at IS NULL",
                    String.class, tenantId.getValue(), userId);
            if (tokens.isEmpty()) return;
            List<FuelExpoPushClient.Message> messages = tokens.stream().map(t -> new FuelExpoPushClient.Message(t, title, body, data)).toList();
            List<FuelExpoPushClient.Result> results = client.send(messages);
            for (int i = 0; i < results.size() && i < tokens.size(); i++) {
                if (results.get(i).deadToken()) {
                    jdbc.update("UPDATE fuel_push_tokens SET revoked_at = NOW(), updated_at = NOW() WHERE tenant_id = ? AND expo_token = ?", tenantId.getValue(), tokens.get(i));
                }
            }
        } catch (Exception e) {
            log.warn("[Fuel] Push to user={} failed: {}", userId, e.toString());
        }
    }
}
