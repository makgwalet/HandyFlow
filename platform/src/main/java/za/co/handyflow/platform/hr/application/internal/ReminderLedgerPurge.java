package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Forgets reminders older than two weeks. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderLedgerPurge {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final int KEEP_DAYS = 14;

    private final ReminderLedger ledger;

    @Scheduled(cron = "0 20 3 * * *", zone = "Africa/Johannesburg")
    public void run() {
        try {
            int n = ledger.purgeBefore(LocalDate.now(SAST).minusDays(KEEP_DAYS));
            if (n > 0) log.info("Forgot {} old reminder record(s)", n);
        } catch (RuntimeException e) {
            log.error("Reminder clean-up failed: {}", e.getMessage());
        }
    }
}
