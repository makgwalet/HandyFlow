package za.co.handyflow.platform.creative.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.creative.application.internal.CreativeService;
import za.co.handyflow.platform.creative.domain.model.CreJob;
import za.co.handyflow.platform.creative.domain.model.CreProof;
import za.co.handyflow.platform.creative.domain.repository.CreJobRepository;
import za.co.handyflow.platform.creative.domain.repository.CreProofRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The two time-driven notifications the gap analysis flagged as missing —
 * nothing "happens" to trigger either one, a clock does, which is why these
 * live in a scheduler rather than CreativeService's request-driven methods.
 * <p>
 * Kept deliberately thin: query, loop, delegate to
 * {@link CreativeService#sendUnapprovedReminder} /
 * {@link CreativeService#sendOverdueAlert} for the actual entity mutation,
 * idempotency check, and email content — same division of responsibility as
 * ContractExpiryScheduler and FleetNotificationScheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeNotificationScheduler {

    private final CreProofRepository proofRepo;
    private final CreJobRepository jobRepo;
    private final CreativeService creativeService;

    @Value("${creative.reminder.unapproved-hours:48}")
    private int unapprovedReminderHours;

    /**
     * A proof sitting PENDING for too long without the client acting on it —
     * runs at 09:00 SAST daily. 48 hours is a reasonable default for "give
     * them a nudge" without being pushy; tune via
     * creative.reminder.unapproved-hours if that's wrong for your clients.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Johannesburg")
    public void checkUnapprovedProofs() {
        Instant cutoff = Instant.now().minus(unapprovedReminderHours, ChronoUnit.HOURS);
        List<CreProof> pending = proofRepo.findPendingNeedingReminder(cutoff);
        for (CreProof p : pending) {
            creativeService.sendUnapprovedReminder(p.getId());
        }
        log.info("[SCHEDULER] Unapproved proof reminder sweep — {} proofs processed", pending.size());
    }

    /**
     * A job past its due date that's still in an active (non-terminal)
     * status — runs at 08:00 SAST daily, same slot Fleet/Contracting use for
     * their own compliance-style sweeps.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    public void checkOverdueJobs() {
        List<CreJob> overdue = jobRepo.findOverdueNeedingAlert(LocalDate.now());
        for (CreJob j : overdue) {
            creativeService.sendOverdueAlert(j.getId());
        }
        log.info("[SCHEDULER] Overdue job alert sweep — {} jobs processed", overdue.size());
    }
}
