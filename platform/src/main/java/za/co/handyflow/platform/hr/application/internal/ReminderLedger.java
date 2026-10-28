package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/**
 * Remembers which reminders went out on which day. {@link #claim} is one atomic insert: the first caller gets true and sends, anyone
 * else (a second run, another instance) gets false and stays quiet. {@link #release} gives the claim back when sending failed, so the
 * next run can try again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderLedger {

    private final JdbcTemplate jdbc;

    public boolean claim(TenantId tenant, LocalDate day, String digest) {
        return jdbc.update("INSERT INTO hr_reminders_sent (tenant_id, sent_on, digest) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                tenant.getValue(), java.sql.Date.valueOf(day), digest) == 1;
    }

    public void release(TenantId tenant, LocalDate day, String digest) {
        try {
            jdbc.update("DELETE FROM hr_reminders_sent WHERE tenant_id = ? AND sent_on = ? AND digest = ?", tenant.getValue(), java.sql.Date.valueOf(day), digest);
        } catch (RuntimeException e) {
            log.warn("Could not give back a reminder claim: {}", e.getMessage());
        }
    }

    public int purgeBefore(LocalDate day) {
        return jdbc.update("DELETE FROM hr_reminders_sent WHERE sent_on < ?", java.sql.Date.valueOf(day));
    }
}
