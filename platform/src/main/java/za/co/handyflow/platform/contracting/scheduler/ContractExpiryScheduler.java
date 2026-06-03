package za.co.handyflow.platform.contracting.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scheduled jobs for contract lifecycle management.
 *
 * FIX §29: ContractRepository.findExpired() existed but was never called.
 * This scheduler calls it daily at 01:00 SAST and transitions SIGNED → EXPIRED.
 *
 * Jobs:
 *   01:00 daily — expire contracts past their end_date
 *   09:00 daily — send 30/14/7/1-day renewal reminder emails
 *
 * For multi-tenant: findAllTenantIds() scans distinct tenant_ids from contracts.
 * In a larger system this would be replaced with a TenantService lookup.
 *
 * Requires: @EnableScheduling on your @SpringBootApplication class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpiryScheduler {

    private final ContractRepository contractRepo;
    private final EmailService       emailService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    /**
     * Expire contracts past their end_date — runs at 01:00 SAST daily.
     * "Africa/Johannesburg" zone is applied via spring.task.scheduling.zone config.
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Africa/Johannesburg")
    public void expireContracts() {
        LocalDate today = LocalDate.now();
        log.info("[SCHEDULER] Checking for expired contracts on {}", today);

        Set<UUID> tenantIds = findAllTenantIds();
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
     * Renewal reminders — runs at 09:00 SAST daily.
     * Sends reminders 30, 14, 7, and 1 days before contract end_date.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Johannesburg")
    public void sendRenewalReminders() {
        LocalDate today = LocalDate.now();
        int[] alertDays = {30, 14, 7, 1};
        log.info("[SCHEDULER] Checking renewal reminders for {}", today);

        Set<UUID> tenantIds = findAllTenantIds();

        for (int days : alertDays) {
            LocalDate alertDate = today.plusDays(days);
            for (UUID rawId : tenantIds) {
                TenantId tenantId = TenantId.of(rawId);
                List<Contract> expiring = contractRepo.findExpiringOn(tenantId, alertDate);
                for (Contract c : expiring) {
                    sendRenewalReminderEmail(c, days);
                }
            }
        }
    }

    private void sendRenewalReminderEmail(Contract c, int daysLeft) {
        String endDate = c.getEndDate() != null ? c.getEndDate().format(DATE_FMT) : "unknown";
        String subject = "Contract expiring in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s")
                + ": " + c.getTitle();
        String body = buildRenewalBody(c.getTitle(), c.getContractNumber(), endDate, daysLeft);

        // Notify all signed parties and the owner
        c.getParties().stream()
                .filter(p -> p.getEmail() != null && !p.getEmail().isBlank())
                .forEach(p -> emailService.send(p.getEmail(), subject, body));

        log.info("[SCHEDULER] Renewal reminder sent for contract={} expiresIn={}d",
                c.getContractNumber(), daysLeft);
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

    /**
     * Finds all distinct tenant IDs that have at least one contract.
     * In production, replace with a TenantService.findAllActive() call.
     */
    private Set<UUID> findAllTenantIds() {
        return contractRepo.findAll().stream()
                .map(c -> c.getTenantId())
                .collect(Collectors.toSet());
    }
}
