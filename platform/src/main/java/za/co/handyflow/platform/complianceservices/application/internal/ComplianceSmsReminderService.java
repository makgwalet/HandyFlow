package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.SmsReminderDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * SMS reminders to clients about deadlines coming up. Only to a number someone has recorded consent for, only while the practice has the
 * feature on, and a withdrawal takes effect immediately. Handing the text to the notification service is all that is known.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceSmsReminderService {

    private final ComplianceSmsSettingsRepository settings;
    private final ComplianceSmsConsentRepository consents;
    private final ComplianceSmsLogRepository smsLog;
    private final ComplianceClientRepository clients;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public SettingsView settings(TenantId tenantId) {
        ComplianceSmsSettings s = settings.findOne(tenantId).orElseGet(() -> ComplianceSmsSettings.blank(tenantId));
        return new SettingsView(s.isEnabled(), s.getUpdatedAt());
    }

    @Transactional
    public SettingsView update(TenantId tenantId, SettingsRequest r, UUID by) {
        ComplianceSmsSettings s = settings.findOne(tenantId).orElseGet(() -> ComplianceSmsSettings.blank(tenantId));
        s.setEnabled(r.enabled(), by);
        settings.save(s);
        return new SettingsView(s.isEnabled(), s.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<ConsentView> consentsFor(TenantId tenantId, UUID clientId) {
        clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
        List<ConsentView> out = new ArrayList<>();
        for (ComplianceSmsConsent c : consents.findForClient(tenantId, clientId)) out.add(view(c));
        return out;
    }

    @Transactional
    public ConsentView recordConsent(TenantId tenantId, UUID clientId, ConsentRequest r, UUID by) {
        clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
        ComplianceSmsConsent c = guard(() -> ComplianceSmsConsent.record(tenantId, clientId, r.contactName(), r.phone(), r.basis(), by));
        for (ComplianceSmsConsent e : consents.findForClient(tenantId, clientId)) {
            if (e.isActive() && e.getPhone().equals(c.getPhone())) throw new HandyFlowException("That number already has consent recorded for this client", HttpStatus.CONFLICT, "SMS_CONSENT_EXISTS");
        }
        return view(consents.save(c));
    }

    @Transactional
    public ConsentView withdraw(TenantId tenantId, UUID consentId, WithdrawRequest r, UUID by) {
        ComplianceSmsConsent c = consents.findOne(tenantId, consentId).orElseThrow(() -> new ResourceNotFoundException("ComplianceSmsConsent", consentId.toString()));
        guard(() -> { c.withdraw(by, r.note()); return null; });
        return view(consents.save(c));
    }

    @Transactional(readOnly = true)
    public List<LogView> log(TenantId tenantId, UUID clientId) {
        List<ComplianceSmsLog> rows = clientId == null ? smsLog.findRecent(tenantId, PageRequest.of(0, 100)) : smsLog.findForClient(tenantId, clientId, PageRequest.of(0, 100));
        Map<UUID, String> names = new HashMap<>();
        List<LogView> out = new ArrayList<>();
        for (ComplianceSmsLog l : rows) {
            String name = names.computeIfAbsent(l.getClientId(), id -> clients.findByIdForTenant(tenantId, id).map(ComplianceClient::getName).orElse("Unknown client"));
            out.add(new LogView(l.getId(), l.getClientId(), name, l.getDeadlineId(), l.getKind(), l.getRunDate(), l.getOutcome(), SmsReminderRules.outcomeText(l.getOutcome()), l.getDetail(), l.getCreatedAt()));
        }
        return out;
    }

    /** One text, at most once per deadline, number and kind. Consent is checked again here, so a withdrawal made mid-run is respected. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String sendOne(TenantId tenantId, ClientComplianceDeadline d, ComplianceSmsConsent c, String kind, LocalDate today) {
        if (!smsLog.findExisting(tenantId, d.getId(), c.getId(), kind).isEmpty()) return "ALREADY_SENT";
        ComplianceSmsConsent fresh = consents.findOne(tenantId, c.getId()).orElse(null);
        if (fresh == null || !fresh.isActive()) return "NO_CONSENT";
        String text = SmsReminderRules.message(d.getDescription(), d.getDueDate(), today);
        notifications.send(NotificationRequest.builder()
                .tenantId(tenantId).type(NotificationType.COMPLIANCESERVICES_CLIENT_SMS)
                .title("Deadline reminder").message(text)
                .sourceModule("complianceservices").sourceEntityId(d.getId().toString())
                .recipients(List.of(Recipient.external(fresh.getContactName(), null, fresh.getPhone()))).build());
        smsLog.save(ComplianceSmsLog.of(tenantId, d.getClientId(), d.getId(), c.getId(), kind, today, "HANDED_OVER", null));
        return "HANDED_OVER";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(TenantId tenantId, ClientComplianceDeadline d, ComplianceSmsConsent c, String kind, LocalDate today, String why) {
        if (smsLog.findExisting(tenantId, d.getId(), c.getId(), kind).isEmpty()) smsLog.save(ComplianceSmsLog.of(tenantId, d.getClientId(), d.getId(), c.getId(), kind, today, "FAILED", why));
    }

    private static ConsentView view(ComplianceSmsConsent c) {
        return new ConsentView(c.getId(), c.getClientId(), c.getContactName(), c.getPhone(), c.getBasis(), c.getGivenBy(), c.getGivenAt(),
                c.isActive(), c.getWithdrawnBy(), c.getWithdrawnAt(), c.getWithdrawnNote());
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "SMS_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "SMS_INVALID");
        }
    }
}
