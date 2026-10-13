package za.co.handyflow.platform.clinic.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.clinic.application.internal.StaleVisitRules.Outcome;
import za.co.handyflow.platform.clinic.domain.model.ClinicAppointment;
import za.co.handyflow.platform.clinic.domain.repository.ClinicAppointmentRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Patch 0202: closes visits from earlier days that were left CHECKED_IN, TRIAGED or IN_PROGRESS, following {@link StaleVisitRules}.
 * Runs at 00:20 clinic time and once when the application starts (so visits left open before this patch are closed straight away).
 * On by default; {@code handyflow.clinic.stale-visit-sweep.enabled=false} turns it off. Each appointment is closed in its own
 * transaction, re-read first, so a visit someone is changing at that moment is skipped rather than overwritten.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "handyflow.clinic.stale-visit-sweep.enabled", havingValue = "true", matchIfMissing = true)
public class ClinicStaleVisitSweep {

    static final int MAX_PER_RUN = 2000;

    private final ClinicAppointmentRepository appointmentRepo;
    private final ClinicConsultationRepository consultationRepo;
    private final TransactionTemplate tx;

    public ClinicStaleVisitSweep(ClinicAppointmentRepository appointmentRepo, ClinicConsultationRepository consultationRepo,
                                 PlatformTransactionManager txManager) {
        this.appointmentRepo = appointmentRepo;
        this.consultationRepo = consultationRepo;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(cron = "0 20 0 * * *", zone = "Africa/Johannesburg")
    public void nightly() { run(Instant.now()); }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        try { run(Instant.now()); } catch (RuntimeException e) { log.warn("[Clinic] Stale visit sweep at start-up failed: {}", e.getMessage()); }
    }

    /** One sweep at the given moment; returns how many appointments were closed each way. */
    Map<Outcome, Integer> run(Instant now) {
        Instant today = StaleVisitRules.startOfToday(now, AppointmentRules.CLINIC_ZONE);
        List<ClinicAppointment> open = appointmentRepo.findUnclosedVisitsBefore(today);
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        for (ClinicAppointment a : open.stream().limit(MAX_PER_RUN).toList()) {
            try {
                Outcome o = tx.execute(s -> close(a.getId(), today));
                if (o != null) counts.merge(o, 1, Integer::sum);
            } catch (RuntimeException e) {
                log.warn("[Clinic] Could not close stale visit appointment={}: {}", a.getId(), e.getMessage());
            }
        }
        if (!open.isEmpty()) log.info("[Clinic] Stale visit sweep: {} open from earlier days; {}", open.size(), counts);
        return counts;
    }

    private Outcome close(UUID id, Instant today) {
        ClinicAppointment a = appointmentRepo.findById(id).orElse(null);
        if (a == null || a.getDeletedAt() != null) return null;
        Outcome o = StaleVisitRules.decide(a.getStatus(), a.getScheduledAt(), today, consultationRepo.statusesForAppointment(id));
        switch (o) {
            case COMPLETE -> { a.complete(); appointmentRepo.save(a); }
            case NOT_SEEN -> { a.markNotSeen(); appointmentRepo.save(a); }
            case KEEP -> { }
        }
        return o;
    }
}
