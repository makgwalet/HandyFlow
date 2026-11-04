package za.co.handyflow.platform.projects.application.internal.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Per-person email switch for each Projects event. No row = the event's default. */
@Service
@RequiredArgsConstructor
public class ProjectNotificationSettings {

    private final JdbcTemplate jdbc;

    public record Setting(String event, String label, boolean email, boolean emailByDefault) {}

    @Transactional(readOnly = true)
    public List<Setting> forUser(UUID tenantId, UUID userId) {
        Map<String, Boolean> saved = new HashMap<>();
        jdbc.query("SELECT event, email FROM pm_notification_settings WHERE tenant_id = ? AND user_id = ?",
                rs -> { saved.put(rs.getString("event"), rs.getBoolean("email")); }, tenantId, userId);
        return Arrays.stream(PmEvent.values())
                .map(e -> new Setting(e.name(), e.label(), saved.getOrDefault(e.name(), e.emailByDefault()), e.emailByDefault())).toList();
    }

    @Transactional
    public List<Setting> update(UUID tenantId, UUID userId, Map<String, Boolean> email) {
        for (var entry : email.entrySet()) {
            PmEvent e;
            try { e = PmEvent.valueOf(entry.getKey()); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Unknown event: " + entry.getKey()); }
            if (entry.getValue() == null) continue;
            jdbc.update("""
                    INSERT INTO pm_notification_settings (tenant_id, user_id, event, email, updated_at) VALUES (?, ?, ?, ?, now())
                    ON CONFLICT (tenant_id, user_id, event) DO UPDATE SET email = EXCLUDED.email, updated_at = now()
                    """, tenantId, userId, e.name(), entry.getValue());
        }
        return forUser(tenantId, userId);
    }

    /** Which of these people want email for this event. */
    @Transactional(readOnly = true)
    public Set<UUID> wantingEmail(UUID tenantId, PmEvent event, Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Set.of();
        Set<UUID> off = new HashSet<>(), on = new HashSet<>();
        jdbc.query("SELECT user_id, email FROM pm_notification_settings WHERE tenant_id = ? AND event = ?",
                rs -> { UUID u = (UUID) rs.getObject("user_id"); if (rs.getBoolean("email")) on.add(u); else off.add(u); }, tenantId, event.name());
        Set<UUID> out = new HashSet<>();
        for (UUID u : userIds) if (event.emailByDefault() ? !off.contains(u) : on.contains(u)) out.add(u);
        return out;
    }

    /** True the first time an item is reminded about today; false if it already was. */
    @Transactional
    public boolean firstToday(UUID tenantId, PmEvent event, UUID entityId, java.time.LocalDate today) {
        return jdbc.update("INSERT INTO pm_notification_sent (tenant_id, event, entity_id, sent_on) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                tenantId, event.name(), entityId, java.sql.Date.valueOf(today)) == 1;
    }
}
