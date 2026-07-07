package za.co.handyflow.platform.contracting.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.repository.ContractPartyRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled jobs for contract lifecycle management.
 *
 * *** THREE FIXES IN THIS VERSION — read all three before changing this
 * class again ***
 * <p>
 * 1. THE SILENT FAILURE: the previous version called {@code c.getParties()}
 * on Contract entities loaded directly from this scheduler's own repository
 * calls. {@code Contract.parties} is {@code @Transient} and starts empty on
 * every entity unless something explicitly calls {@code setParties(...)}
 * after a separate {@code ContractPartyRepository} query — see Contract's
 * Javadoc. Nothing here ever did that, so every renewal reminder email
 * silently iterated an empty list and sent nothing, while still logging
 * "Renewal reminder sent" right after — a false success signal. Fixed by
 * injecting {@link ContractPartyRepository} and calling
 * {@code setParties(partyRepo.findByContract(c.getId()))} before touching
 * {@code c.getParties()} anywhere.
 * <p>
 * 2. THE SCALABILITY BUG: {@code findAllTenantIds()} called
 * {@code contractRepo.findAll()} — loading every Contract row across every
 * tenant and status, full TEXT body column included, just to extract a
 * {@code Set<UUID>}. Replaced with
 * {@link ContractRepository#findDistinctActiveTenantIds()}, a genuine
 * single-column DISTINCT query.
 * <p>
 * 3. THE DEAD COLUMNS: V56 added reminder_30/14/7/1_sent_at specifically so
 * this scheduler could track which thresholds have already fired and catch
 * up if a day's run is missed — but the code used only an exact
 * {@code endDate = today+N} match with no persistence of what had already
 * been sent. That means a missed run permanently skips that contract's
 * reminder for that threshold — no way to catch up. Fixed by switching to a
 * range query ({@link ContractRepository#findSignedExpiringWithin}) plus
 * {@code Contract.isReminderSent()}/{@code markReminderSent()}, which
 * actually uses those columns and self-heals across missed runs.
 * <p>
 * KNOWN REMAINING GAP: "notify the owner" (the doc comment on the original
 * version's intent) isn't implemented — there's no user-lookup port
 * available to this module to resolve {@code Contract.createdBy} to an
 * email address, mirroring the same integration gap already solved for
 * Earthmoving/Fleet via {@code TenantAdminRecipients}. Until a dedicated
 * "resolve this specific user" lookup exists, reminders go out to contract
 * parties with a real email on file only. Worth revisiting once that lookup
 * exists rather than guessing at one here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpiryScheduler {

    private static final int[] REMINDER_THRESHOLDS = {30, 14, 7, 1};

    private final ContractRepository contractRepo;
    private final ContractPartyRepository partyRepo;
    private final EmailService emailService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    @Scheduled(cron = "0 0 1 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void expireContracts() {
        LocalDate today = LocalDate.now();
        log.info("[SCHEDULER] Checking for expired contracts on {}", today);

        List<UUID> tenantIds = contractRepo.findDistinctActiveTenantIds();
        int count = 0;

        for (UUID rawId : tenantIds) {
            TenantId tenantId = TenantId.of(rawId);
            List<Contract> expired = contractRepo.findExpired(tenantId, today);
            for (Contract c : expired) {
                try {
                    c.expire();
                    contractRepo.save(c);
                    log.info("[SCHEDULER] Expired contract={} tenant={}", c.getContractNumber(), rawId);
                    count++;
                } catch (Exception e) {
                    log.error("[SCHEDULER] Failed to expire contract={}: {}",
                            c.getContractNumber(), e.getMessage());
                }
            }
        }
        log.info("[SCHEDULER] Expiry run complete — {} contracts expired", count);
    }

    /**
     * Renewal reminders — runs at 09:00 SAST daily. Range-based and
     * idempotency-tracked per threshold (see class Javadoc, fix #3) instead
     * of the previous exact-date match.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void sendRenewalReminders() {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(REMINDER_THRESHOLDS[0]);
        log.info("[SCHEDULER] Checking renewal reminders for {}", today);

        List<UUID> tenantIds = contractRepo.findDistinctActiveTenantIds();
        int sent = 0;

        for (UUID rawId : tenantIds) {
            TenantId tenantId = TenantId.of(rawId);
            List<Contract> candidates = contractRepo.findSignedExpiringWithin(tenantId, today, cutoff);

            for (Contract c : candidates) {
                long daysUntil = ChronoUnit.DAYS.between(today, c.getEndDate());
                for (int threshold : REMINDER_THRESHOLDS) {
                    if (daysUntil <= threshold && !c.isReminderSent(threshold)) {
                        if (sendRenewalReminderEmail(c, (int) daysUntil)) {
                            sent++;
                        }
                        c.markReminderSent(threshold);
                        contractRepo.save(c);
                        break;
                    }
                }
            }
        }
        log.info("[SCHEDULER] Renewal reminder run complete — {} emails sent", sent);
    }

    /**
     * FIX #1: parties are now actually loaded before being used — previously
     * this always iterated an empty transient list. Returns true if at least
     * one email was actually sent, so the caller can report a real count
     * instead of assuming success.
     */
    private boolean sendRenewalReminderEmail(Contract c, int daysLeft) {
        List<ContractParty> parties = partyRepo.findByContract(c.getId());
        c.setParties(parties);

        String endDate = c.getEndDate() != null ? c.getEndDate().format(DATE_FMT) : "unknown";
        String subject = "Contract expiring in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s")
                + ": " + c.getTitle();
        String body = buildRenewalBody(c.getTitle(), c.getContractNumber(), endDate, daysLeft);

        boolean anySent = false;
        for (ContractParty p : parties) {
            if (p.getEmail() != null && !p.getEmail().isBlank()) {
                emailService.send(p.getEmail(), subject, body);
                anySent = true;
            }
        }

        if (anySent) {
            log.info("[SCHEDULER] Renewal reminder sent for contract={} expiresIn={}d recipients={}",
                    c.getContractNumber(), daysLeft, parties.size());
        } else {
            log.warn("[SCHEDULER] Renewal reminder due for contract={} expiresIn={}d but no party had a usable "
                    + "email address — nobody was actually notified", c.getContractNumber(), daysLeft);
        }
        return anySent;
    }

    private String buildRenewalBody(String title, String number, String endDate, int daysLeft) {
        String urgency = daysLeft <= 7 ? "is expiring very soon" : "is coming up for renewal";
        return """
            <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;max-width:560px;margin:40px auto;">
            <div style="background:#1B3A6B;padding:20px 28px;border-radius:12px 12px 0 0;">
              <h1 style="color:white;margin:0;font-size:18px;">HandyFlow</h1>
            </div>
            <div style="background:white;padding:28px;border:1px solid #E2E8F0;border-radius:0 0 12px 12px;">
              <p style="color:#374151;font-size:14px;">Your contract <strong>%s</strong> (%s) %s.</p>
              <div style="background:#FEF3C7;border-left:3px solid #D97706;padding:12px 16px;margin:16px 0;border-radius:0 8px 8px 0;">
                <p style="margin:0;color:#92400E;font-weight:600;">Expires in <strong>%d day%s</strong> — on %s</p>
              </div>
              <p style="color:#374151;font-size:14px;">Log into HandyFlow to review renewal options or create a new contract.</p>
            </div>
            </body></html>
            """.formatted(title, number, urgency, daysLeft, daysLeft == 1 ? "" : "s", endDate);
    }
}
