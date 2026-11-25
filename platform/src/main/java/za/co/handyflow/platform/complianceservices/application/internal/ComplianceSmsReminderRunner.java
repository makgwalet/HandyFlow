package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.SmsReminderDtos.RunResult;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/** Works through the deadlines coming up and sends each consenting number its reminder, one text at a time so one failure stops no other. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceSmsReminderRunner {

    private final ComplianceSmsSettingsRepository settings;
    private final ComplianceSmsConsentRepository consents;
    private final ClientComplianceDeadlineRepository deadlines;
    private final ComplianceSmsReminderService service;

    /** Every tenant that has SMS reminders on. One tenant failing does not stop the others. */
    public void runAll(LocalDate today) {
        for (ComplianceSmsSettings s : settings.findAllEnabled()) {
            try {
                RunResult r = run(s.getTenantId(), today);
                log.info("[ComplianceServices] SMS reminders for tenant {}: {} handed over, {} failed, {} already sent", s.getTenantId().getValue(), r.handedOver(), r.failed(), r.alreadySent());
            } catch (RuntimeException e) {
                log.warn("[ComplianceServices] SMS reminders for tenant {} failed: {}", s.getTenantId().getValue(), e.getMessage());
            }
        }
    }

    /** Today's reminders for one tenant. Refuses when the feature is off. Not transactional itself: each text is recorded on its own. */
    public RunResult run(TenantId tenantId, LocalDate today) {
        ComplianceSmsSettings s = settings.findOne(tenantId).filter(ComplianceSmsSettings::isEnabled)
                .orElseThrow(() -> new HandyFlowException("Switch SMS reminders on first", HttpStatus.CONFLICT, "SMS_OFF"));
        Map<UUID, List<ComplianceSmsConsent>> byClient = new HashMap<>();
        for (ComplianceSmsConsent c : consents.findActive(s.getTenantId())) byClient.computeIfAbsent(c.getClientId(), k -> new ArrayList<>()).add(c);
        int handed = 0, failed = 0, already = 0, noConsent = 0;
        for (ClientComplianceDeadline d : deadlines.findPendingDueBy(tenantId, today.plusDays(7))) {
            String kind = SmsReminderRules.kindFor(d.getDueDate(), today);
            if (kind == null) continue;
            List<ComplianceSmsConsent> people = byClient.getOrDefault(d.getClientId(), List.of());
            if (people.isEmpty()) { noConsent++; continue; }
            for (ComplianceSmsConsent c : people) {
                try {
                    switch (service.sendOne(tenantId, d, c, kind, today)) {
                        case "HANDED_OVER" -> handed++;
                        case "ALREADY_SENT" -> already++;
                        case "NO_CONSENT" -> noConsent++;
                        default -> failed++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    try { service.recordFailure(tenantId, d, c, kind, today, e.getMessage()); } catch (RuntimeException ignored) { /* best effort */ }
                }
            }
        }
        return new RunResult(handed, failed, already, noConsent);
    }
}
