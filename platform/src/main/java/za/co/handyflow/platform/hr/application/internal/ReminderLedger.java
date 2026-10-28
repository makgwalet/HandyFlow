package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.ReminderKeyRules;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Remembers which reminders went out on which day. {@link #claim} is one atomic insert: the first caller gets true and sends, anyone
 * else (a second run, another instance) gets false and stays quiet. {@link #release} gives the claim back when sending failed, so the
 * next run can try again. Every call runs in its own transaction: the reminder jobs that call it are read-only, and a claim must be
 * saved at once, not when (or if) the job ends.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderLedger {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(TenantId tenant, LocalDate day, String digest) {
        return jdbc.update("INSERT INTO hr_reminders_sent (tenant_id, sent_on, digest) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                tenant.getValue(), java.sql.Date.valueOf(day), digest) == 1;
    }

    /**
     * The people in {@code to} who have not been sent exactly this message today, claiming it for each of them. The claims are added to
     * {@code claimed} so the caller can {@link #release} them if sending fails. Someone nothing identifies is always kept. All the claims are
     * saved together, or none are if the ledger cannot be reached; callers then send to everyone rather than stay silent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Recipient> unclaimed(TenantId tenant, LocalDate day, String type, String subject, String title, String message,
                                     List<Recipient> to, List<String> claimed) {
        List<Recipient> fresh = new ArrayList<>();
        for (Recipient r : to) {
            String who = ReminderKeyRules.person(r.userId(), r.email(), r.displayName());
            if (who == null) { fresh.add(r); continue; }
            String digest = ReminderKeyRules.key(type, subject, who, title, message);
            if (claim(tenant, day, digest)) { fresh.add(r); claimed.add(digest); }
        }
        return fresh;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
