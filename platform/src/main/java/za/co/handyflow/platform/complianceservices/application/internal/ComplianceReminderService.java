package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ReminderDtos.*;
import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.QueueEntry;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * The daily digest of the work queue. It repeats what is already recorded; it never creates work. Handing a message to the notification
 * service is all that is known, so the log never says "delivered" or "read".
 */
@Service
@RequiredArgsConstructor
public class ComplianceReminderService {

    public static final String ALREADY_DONE = "ALREADY_DONE";

    private final ComplianceReminderSettingsRepository settings;
    private final ComplianceReminderRecipientRepository recipients;
    private final ComplianceReminderLogRepository log;
    private final ComplianceWorkQueueService queue;
    private final UserRecipientResolver resolver;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public SettingsView settings(TenantId tenantId) {
        ComplianceReminderSettings s = settings.findOne(tenantId).orElseGet(() -> ComplianceReminderSettings.blank(tenantId));
        return view(tenantId, s, recipients.findForTenant(tenantId));
    }

    @Transactional
    public SettingsView update(TenantId tenantId, SettingsRequest r, UUID by) {
        List<RecipientRequest> wanted = r.recipients() == null ? List.of() : r.recipients();
        Set<UUID> seen = new HashSet<>();
        for (RecipientRequest w : wanted) {
            if (!seen.add(w.userId())) throw new HandyFlowException("The same person is listed twice", HttpStatus.BAD_REQUEST, "REMINDER_RECIPIENT");
            if (resolver.resolveUser(tenantId, w.userId()).isEmpty()) throw new HandyFlowException("One of the people chosen could not be found", HttpStatus.BAD_REQUEST, "REMINDER_RECIPIENT");
        }
        ComplianceReminderSettings s = settings.findOne(tenantId).orElseGet(() -> ComplianceReminderSettings.blank(tenantId));
        guard(() -> { s.apply(r.enabled(), r.minSeverity(), r.maxItems(), wanted.size(), by); return null; });
        settings.save(s);

        Map<UUID, ComplianceReminderRecipient> existing = new HashMap<>();
        for (ComplianceReminderRecipient e : recipients.findForTenant(tenantId)) existing.put(e.getUserId(), e);
        for (RecipientRequest w : wanted) {
            ComplianceReminderRecipient e = existing.remove(w.userId());
            if (e == null) recipients.save(ComplianceReminderRecipient.of(tenantId, w.userId(), w.adminItems(), by));
            else { e.setAdminItems(w.adminItems()); recipients.save(e); }
        }
        recipients.deleteAll(existing.values());
        return view(tenantId, s, recipients.findForTenant(tenantId));
    }

    /** What each person would be sent today. Sends nothing. */
    @Transactional(readOnly = true)
    public List<PreviewView> preview(TenantId tenantId, LocalDate today) {
        ComplianceReminderSettings s = settings.findOne(tenantId).orElseGet(() -> ComplianceReminderSettings.blank(tenantId));
        List<PreviewView> out = new ArrayList<>();
        for (ComplianceReminderRecipient rec : recipients.findForTenant(tenantId)) {
            ReminderRules.Selection sel = selection(tenantId, s, rec, today);
            out.add(new PreviewView(rec.getUserId(), nameOf(tenantId, rec.getUserId()), rec.isAdminItems(), sel.total(),
                    sel.total() == 0 ? "Nothing to send" : ReminderRules.subject(sel), sel.total() == 0 ? "" : ReminderRules.body(today, sel)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<LogView> log(TenantId tenantId) {
        List<LogView> out = new ArrayList<>();
        Map<UUID, String> names = new HashMap<>();
        for (ComplianceReminderLog l : log.findRecent(tenantId, PageRequest.of(0, 100))) {
            String name = names.computeIfAbsent(l.getUserId(), id -> nameOf(tenantId, id));
            out.add(new LogView(l.getRunDate(), l.getUserId(), name, l.getItemCount(), l.getOutcome(), ReminderRules.outcomeText(l.getOutcome()), l.getDetail(), l.getCreatedAt()));
        }
        return out;
    }

    /** One person's digest for one day. Done at most once a day: a second call does nothing. Returns the outcome, or ALREADY_DONE. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String sendTo(TenantId tenantId, ComplianceReminderSettings s, ComplianceReminderRecipient rec, LocalDate today) {
        if (!log.findForDay(tenantId, rec.getUserId(), today).isEmpty()) return ALREADY_DONE;
        Optional<Recipient> person = resolver.resolveUser(tenantId, rec.getUserId());
        if (person.isEmpty()) {
            log.save(ComplianceReminderLog.of(tenantId, rec.getUserId(), today, 0, "NO_ADDRESS", "The person could not be found"));
            return "NO_ADDRESS";
        }
        ReminderRules.Selection sel = selection(tenantId, s, rec, today);
        if (sel.total() == 0) {
            log.save(ComplianceReminderLog.of(tenantId, rec.getUserId(), today, 0, "NOTHING_DUE", null));
            return "NOTHING_DUE";
        }
        notifications.send(NotificationRequest.builder()
                .tenantId(tenantId).type(NotificationType.COMPLIANCESERVICES_DAILY_DIGEST)
                .title(ReminderRules.subject(sel)).message(ReminderRules.body(today, sel))
                .actionUrl("/complianceservices/queue").sourceModule("complianceservices").sourceEntityId(today.toString())
                .recipients(List.of(person.get())).build());
        log.save(ComplianceReminderLog.of(tenantId, rec.getUserId(), today, sel.total(), "HANDED_OVER", null));
        return "HANDED_OVER";
    }

    /** Written on its own, so a failed hand-over still leaves a record. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(TenantId tenantId, UUID userId, LocalDate today, String why) {
        if (log.findForDay(tenantId, userId, today).isEmpty()) log.save(ComplianceReminderLog.of(tenantId, userId, today, 0, "FAILED", why));
    }

    private ReminderRules.Selection selection(TenantId tenantId, ComplianceReminderSettings s, ComplianceReminderRecipient rec, LocalDate today) {
        List<ReminderRules.Line> lines = new ArrayList<>();
        for (QueueEntry e : queue.queue(tenantId, rec.isAdminItems(), today).items()) lines.add(new ReminderRules.Line(e.severity(), e.clientName(), e.title(), e.detail()));
        return ReminderRules.select(lines, s.getMinSeverity(), s.getMaxItems());
    }

    private String nameOf(TenantId tenantId, UUID userId) {
        return resolver.resolveUser(tenantId, userId).map(Recipient::displayName).orElse("Unknown person");
    }

    private SettingsView view(TenantId tenantId, ComplianceReminderSettings s, List<ComplianceReminderRecipient> recs) {
        List<RecipientView> people = new ArrayList<>();
        for (ComplianceReminderRecipient r : recs) {
            Optional<Recipient> p = resolver.resolveUser(tenantId, r.getUserId());
            people.add(new RecipientView(r.getUserId(), p.map(Recipient::displayName).orElse("Unknown person"), p.map(Recipient::email).orElse(null), r.isAdminItems()));
        }
        return new SettingsView(s.isEnabled(), s.getMinSeverity(), s.getMaxItems(), people, s.getUpdatedAt());
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "REMINDER_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "REMINDER_INVALID");
        }
    }
}
