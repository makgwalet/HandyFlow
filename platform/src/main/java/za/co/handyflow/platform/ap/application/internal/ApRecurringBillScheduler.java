package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Separate scheduled class, same convention as ApBillDueSoonScheduler —
 * runs once daily at 06:30, deliberately before the 07:00 due-soon
 * reminder job, so a bill generated this morning already has a chance to
 * appear in that same day's due-soon sweep if it happens to qualify.
 * <p>
 * ASSUMES @EnableScheduling IS ALREADY ON — same assumption
 * ApBillDueSoonScheduler already makes, not re-verified here.
 */
@Component
@RequiredArgsConstructor
public class ApRecurringBillScheduler {

    private final ApRecurringBillService recurringBillService;

    @Scheduled(cron = "0 30 6 * * *") // once daily, 06:30
    public void generateDueRecurringBills() {
        recurringBillService.generateDueBills();
    }
}