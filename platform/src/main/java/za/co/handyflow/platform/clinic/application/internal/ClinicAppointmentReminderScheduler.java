package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.clinic.domain.model.ClinicAppointment;
import za.co.handyflow.platform.clinic.domain.repository.ClinicAppointmentRepository;

import java.time.Instant;
import java.util.List;

/**
 * Sends appointment reminders automatically. Off unless {@code handyflow.clinic.reminders.enabled=true},
 * so deploying this never starts emailing patients by surprise.
 * <p>
 * Every hour (clinic time) it looks for SCHEDULED or CONFIRMED appointments starting between 2 and 24 hours
 * from now that have not been reminded, and only sends between 08:00 and 20:00 clinic time. Each appointment is
 * claimed with a single UPDATE on {@code reminder_sent_at}, so a second instance or an overlapping run cannot
 * send the same reminder twice. A failure on one appointment does not stop the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "handyflow.clinic.reminders.enabled", havingValue = "true")
public class ClinicAppointmentReminderScheduler {

    /** Upper bound per run so a backlog cannot hold the thread for long; the next run carries on. */
    static final int MAX_PER_RUN = 500;

    private final ClinicAppointmentRepository appointmentRepo;
    private final ClinicAppointmentReminderService reminderService;

    @Scheduled(cron = "0 5 * * * *", zone = "Africa/Johannesburg")
    public void sweep() {
        run(Instant.now());
    }

    /** One sweep at the given moment; returns how many reminders were sent. */
    int run(Instant now) {
        if (!ReminderRules.inSendingHours(now, ReminderRules.CLINIC_ZONE)) return 0;
        List<ClinicAppointment> due = appointmentRepo.findDueForReminder(ReminderRules.from(now), ReminderRules.to(now));
        int sent = 0;
        for (ClinicAppointment a : due.stream().limit(MAX_PER_RUN).toList()) {
            try {
                if (!reminderService.claimReminder(a.getId())) continue;
                reminderService.sendReminder(a.getId());
                sent++;
            } catch (Exception e) {
                log.warn("[Clinic] Automatic reminder failed for appointment={}: {}", a.getId(), e.getMessage());
            }
        }
        if (!due.isEmpty()) log.info("[Clinic] Reminder sweep: {} due, {} sent", due.size(), sent);
        return sent;
    }
}
