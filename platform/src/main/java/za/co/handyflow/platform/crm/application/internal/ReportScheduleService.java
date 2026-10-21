package za.co.handyflow.platform.crm.application.internal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.application.internal.ReportDigest.FunnelRow;
import za.co.handyflow.platform.crm.application.internal.ReportDigest.Lead;
import za.co.handyflow.platform.crm.application.internal.ReportDigest.SourceRow;
import za.co.handyflow.platform.crm.domain.model.ReportSchedule;
import za.co.handyflow.platform.crm.domain.model.ReportType;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.ReportScheduleRepository;
import za.co.handyflow.platform.crm.dto.ReportPreviewResponse;
import za.co.handyflow.platform.crm.dto.ReportScheduleRequest;
import za.co.handyflow.platform.crm.dto.ReportScheduleResponse;
import za.co.handyflow.platform.crm.dto.ReportSendResponse;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** Report schedules, and sending the reports that are due. A report that cannot be sent is tried again on its next day, never twice in one day. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportScheduleService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final ReportScheduleRepository scheduleRepository;
    private final CustomerRepository customerRepository;
    private final CrmReportingService reportingService;
    private final UserRecipientResolver userResolver;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ReportScheduleResponse> list(TenantId tenantId) {
        return scheduleRepository.findAllActive(tenantId).stream().map(ReportScheduleService::toResponse).toList();
    }

    @Transactional
    public ReportScheduleResponse create(TenantId tenantId, ReportScheduleRequest req) {
        var ids = validate(tenantId, req);
        if (scheduleRepository.findAllActive(tenantId).size() >= ReportRules.MAX_SCHEDULES) throw new IllegalArgumentException("At most " + ReportRules.MAX_SCHEDULES + " scheduled reports");
        return toResponse(scheduleRepository.save(ReportSchedule.create(tenantId, req.name(), req.reportType(), req.frequency(), LeadRouting.joinIds(ids), req.active() == null || req.active())));
    }

    @Transactional
    public ReportScheduleResponse update(TenantId tenantId, UUID id, ReportScheduleRequest req) {
        var s = require(tenantId, id);
        var ids = validate(tenantId, req);
        s.change(req.name(), req.reportType(), req.frequency(), LeadRouting.joinIds(ids), req.active() == null || req.active());
        return toResponse(scheduleRepository.save(s));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        var s = require(tenantId, id);
        s.softDelete();
        scheduleRepository.save(s);
    }

    @Transactional(readOnly = true)
    public ReportPreviewResponse preview(TenantId tenantId, ReportType type) {
        if (type == null) throw new IllegalArgumentException("Choose which report to send");
        var m = build(tenantId, type, LocalDate.now(ZONE));
        return new ReportPreviewResponse(m.title(), m.body());
    }

    /** Sends the report now. Does not count as the scheduled send, so the next scheduled day still goes out. */
    @Transactional(readOnly = true)
    public ReportSendResponse sendNow(TenantId tenantId, UUID id) {
        return new ReportSendResponse(deliver(tenantId, require(tenantId, id)));
    }

    /** Sends every active report that is due today. Returns how many went out. Never throws. */
    @Transactional
    public int sendDue(TenantId tenantId, LocalDate today) {
        int sent = 0;
        for (ReportSchedule s : scheduleRepository.findAllActive(tenantId)) {
            if (!s.isActive()) continue;
            LocalDate last = s.getLastSentAt() == null ? null : s.getLastSentAt().atZone(ZONE).toLocalDate();
            if (!ReportRules.isDue(s.getFrequency(), last, today)) continue;
            try {
                if (deliver(tenantId, s) > 0) { s.markSent(); scheduleRepository.save(s); sent++; }
            } catch (Exception e) {
                log.warn("[CRM] Scheduled report {} could not be sent tenant={}: {}", s.getId(), tenantId, e.getMessage());
            }
        }
        return sent;
    }

    private int deliver(TenantId tenantId, ReportSchedule s) {
        List<Recipient> to = new ArrayList<>();
        for (UUID u : LeadRouting.parseIds(s.getRecipientIds())) userResolver.resolveUser(tenantId, u).ifPresent(to::add);
        if (to.isEmpty()) to = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (to.isEmpty()) throw new IllegalStateException("Nobody could be found to send the report to");
        var m = build(tenantId, s.getReportType(), LocalDate.now(ZONE));
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId).type(NotificationType.CRM_REPORT_DIGEST).title(m.title()).message(m.body())
                .actionUrl("/crm").sourceModule("crm").sourceEntityId(s.getId().toString()).recipients(to).build());
        return to.size();
    }

    private ReportDigest.Message build(TenantId tenantId, ReportType type, LocalDate today) {
        return switch (type) {
            case PIPELINE -> ReportDigest.pipeline(customerRepository.findAllActiveLeads(tenantId).stream()
                    .map(c -> new Lead(c.getPipelineStage(), c.getDealValue(), c.getExpectedCloseDate())).toList(), today);
            case SOURCES -> ReportDigest.sources(reportingService.getSourceReport(tenantId).stream()
                    .map(e -> new SourceRow(e.source() == null ? null : pretty(e.source().name()), e.leads(), e.won(), e.lost(), e.winRate())).toList());
            case FUNNEL -> {
                var f = reportingService.getFunnelReport(tenantId);
                yield ReportDigest.funnel(f.stages().stream().map(st -> new FunnelRow(st.stage(), st.reachedCount())).toList(), f.totalLeads(), f.totalWon(), f.overallConversionRate());
            }
        };
    }

    private static String pretty(String enumName) {
        String t = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private List<UUID> validate(TenantId tenantId, ReportScheduleRequest req) {
        List<UUID> ids = req.recipientIds() == null ? List.of() : req.recipientIds();
        ReportRules.check(req.name(), req.reportType(), req.frequency(), ids);
        for (UUID u : ids) {
            if (userResolver.resolveUser(tenantId, u).isEmpty()) throw new IllegalArgumentException("That person is not a user of this account");
        }
        return ids;
    }

    private ReportSchedule require(TenantId tenantId, UUID id) {
        return scheduleRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Report schedule", id.toString()));
    }

    static ReportScheduleResponse toResponse(ReportSchedule s) {
        return new ReportScheduleResponse(s.getId(), s.getName(), s.getReportType(), s.getFrequency(), LeadRouting.parseIds(s.getRecipientIds()), s.isActive(), s.getLastSentAt(),
                ReportRules.describe(s.getReportType(), s.getFrequency()));
    }
}
