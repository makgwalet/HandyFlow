package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceReminderRecipient;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceReminderSettings;
import za.co.handyflow.platform.complianceservices.domain.model.ReminderRules;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceReminderRecipientRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceReminderSettingsRepository;
import za.co.handyflow.platform.complianceservices.dto.ReminderDtos.RunResult;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/** Sends each chosen person's digest, one at a time, so one failure does not stop the others. Used by the daily schedule and by "send now". */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceReminderRunner {

    private final ComplianceReminderSettingsRepository settings;
    private final ComplianceReminderRecipientRepository recipients;
    private final ComplianceReminderService service;

    public RunResult run(TenantId tenantId, LocalDate today) {
        ComplianceReminderSettings s = settings.findOne(tenantId).filter(ComplianceReminderSettings::isEnabled)
                .orElseThrow(() -> new HandyFlowException("Switch the daily digest on first", HttpStatus.CONFLICT, "REMINDER_OFF"));
        return runFor(s, today);
    }

    /** Every tenant that has the digest on. Working days only. */
    public void runAll(LocalDate today) {
        if (!ReminderRules.isSendDay(today)) return;
        for (ComplianceReminderSettings s : settings.findAllEnabled()) {
            try {
                RunResult r = runFor(s, today);
                log.info("[ComplianceServices] digest for tenant {}: {} handed over, {} nothing due, {} failed", s.getTenantId().getValue(), r.handedOver(), r.nothingDue(), r.failed());
            } catch (RuntimeException e) {
                log.warn("[ComplianceServices] digest for tenant {} failed: {}", s.getTenantId().getValue(), e.getMessage());
            }
        }
    }

    private RunResult runFor(ComplianceReminderSettings s, LocalDate today) {
        int handed = 0, nothing = 0, noAddress = 0, failed = 0, already = 0;
        for (ComplianceReminderRecipient rec : recipients.findForTenant(s.getTenantId())) {
            try {
                switch (service.sendTo(s.getTenantId(), s, rec, today)) {
                    case "HANDED_OVER" -> handed++;
                    case "NOTHING_DUE" -> nothing++;
                    case "NO_ADDRESS" -> noAddress++;
                    default -> already++;
                }
            } catch (RuntimeException e) {
                failed++;
                try { service.recordFailure(s.getTenantId(), rec.getUserId(), today, e.getMessage()); } catch (RuntimeException ignored) { /* the record is best effort */ }
            }
        }
        return new RunResult(handed, nothing, noAddress, failed, already);
    }
}
