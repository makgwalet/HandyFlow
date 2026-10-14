package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** Makes the draft journals that recurring schedules owe, once a day (finance F17). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringJournalScheduler {

    private final RecurringJournalService service;

    @Scheduled(cron = "0 30 4 * * *")   // 04:30 UTC = 06:30 SAST, before the working day
    public void makeDueJournals() {
        LocalDate today = LocalDate.now();
        for (UUID[] due : service.dueSchedules(today)) {
            try {
                var result = service.runDue(TenantId.of(due[0]), due[1], today);
                if (result.made() > 0 || !result.problems().isEmpty()) {
                    log.info("Recurring journal {} tenant {}: made {} problems {}", due[1], due[0], result.made(), result.problems());
                }
            } catch (Exception e) {
                log.warn("Recurring journal {} tenant {} failed: {}", due[1], due[0], e.getMessage());
            }
        }
    }
}
