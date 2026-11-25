package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.DashboardDtos.*;
import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.QueueEntry;
import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.WorkQueueResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The practice at a glance. It counts what is already recorded and shows the work queue's most urgent items; it records nothing and gives
 * no score. Only active clients count towards deadlines and the queue, as everywhere else.
 */
@Service
@RequiredArgsConstructor
public class ComplianceDashboardService {

    private static final int URGENT_SHOWN = 6;
    private static final int UPCOMING_SHOWN = 6;
    private static final int UPCOMING_DAYS = 30;

    private final ComplianceWorkQueueService queueService;
    private final ComplianceClientRepository clients;
    private final ClientComplianceDeadlineRepository deadlines;
    private final ComplianceQuoteRepository quotes;
    private final ComplianceAgreementRepository agreements;
    private final ComplianceBillingEventRepository billing;
    private final ClientTenderRepository tenders;

    @Transactional(readOnly = true)
    public Summary summary(TenantId tenantId, boolean admin, LocalDate today) {
        WorkQueueResponse queue = queueService.queue(tenantId, admin, today);
        List<ComplianceClient> allClients = clients.findAll(tenantId, null, Pageable.unpaged()).getContent();
        Map<UUID, String> active = allClients.stream().filter(c -> "ACTIVE".equals(c.getStatus())).collect(Collectors.toMap(ComplianceClient::getId, ComplianceClient::getName));
        Map<String, Integer> byStatus = new TreeMap<>();
        allClients.forEach(c -> byStatus.merge(c.getStatus(), 1, Integer::sum));

        // deadlines: everything pending for an active client, up to the end of the weekly view
        List<ClientComplianceDeadline> pending = deadlines.findPendingDueBy(tenantId, today.plusDays(7L * DashboardRules.WEEKS)).stream()
                .filter(d -> active.containsKey(d.getClientId())).toList();
        List<LocalDate> dueDates = pending.stream().map(ClientComplianceDeadline::getDueDate).toList();
        int overdue = DashboardRules.overdue(today, dueDates);
        int next14 = (int) dueDates.stream().filter(d -> !d.isBefore(today) && !d.isAfter(today.plusDays(14))).count();
        List<Upcoming> upcoming = pending.stream().filter(d -> !d.getDueDate().isAfter(today.plusDays(UPCOMING_DAYS))).limit(UPCOMING_SHOWN)
                .map(d -> new Upcoming(d.getClientId(), active.get(d.getClientId()), label(d), d.getDueDate())).toList();

        // quotes and tenders
        List<ComplianceQuote> allQuotes = quotes.findAllForTenant(tenantId);
        List<String> quoteStages = new ArrayList<>();
        List<BigDecimal> quoteTotals = new ArrayList<>();
        for (ComplianceQuote q : allQuotes) {
            quoteStages.add(DashboardRules.quoteStage(q.getStatus(), q.getValidUntil(), today));
            quoteTotals.add(q.getTotal());
        }
        List<Stage> quotePipeline = toStages(DashboardRules.pipeline(DashboardRules.QUOTE_ORDER, quoteStages, quoteTotals));
        List<ClientTender> allTenders = tenders.findAllForTenant(tenantId);
        List<Stage> tenderPipeline = toStages(DashboardRules.pipeline(DashboardRules.TENDER_ORDER, allTenders.stream().map(ClientTender::getStatus).toList(),
                allTenders.stream().map(ClientTender::getEstimatedValue).toList()));
        int quotesOut = 0;
        BigDecimal quotesOutValue = BigDecimal.ZERO;
        for (int i = 0; i < allQuotes.size(); i++) {
            if (!"SENT".equals(quoteStages.get(i))) continue;
            quotesOut++;
            quotesOutValue = quotesOutValue.add(allQuotes.get(i).getTotal());
        }

        // agreements and billing
        int activeAgreements = (int) agreements.findAllForTenant(tenantId).stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
        List<ComplianceBillingEvent> scheduled = billing.findAllForTenant(tenantId).stream().filter(e -> BillingRules.SCHEDULED.equals(e.getStatus())).toList();
        List<DashboardRules.Dated> dated = scheduled.stream().map(e -> new DashboardRules.Dated(e.getDueDate(), e.getAmount())).toList();
        List<ComplianceBillingEvent> toRaise = scheduled.stream().filter(e -> !e.getDueDate().isAfter(today.plusDays(BillingRules.LEAD_DAYS))).toList();
        BigDecimal toRaiseValue = toRaise.stream().map(ComplianceBillingEvent::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        DashboardRules.Behind behind = DashboardRules.behind(today, dated);

        Kpis kpis = new Kpis(active.size(), overdue, next14, countType(queue, "DOCUMENT_REVIEW"), countType(queue, "DOCUMENT_EXPIRY"), countType(queue, "CASE"),
                countType(queue, "REGULATORY_IMPACT"), quotesOut, quotesOutValue, activeAgreements, toRaise.size(), toRaiseValue);
        return new Summary(today, kpis, queue.counts(), byStatus.entrySet().stream().map(e -> new Slice(e.getKey(), e.getValue())).toList(), quotePipeline, tenderPipeline,
                DashboardRules.weeks(today, dueDates).stream().map(w -> new Week(w.start(), w.count())).toList(), overdue,
                DashboardRules.months(today, dated).stream().map(m -> new Month(m.month(), m.amount(), m.count())).toList(), behind.amount(), behind.count(),
                queue.items().stream().limit(URGENT_SHOWN).toList(), upcoming);
    }

    private static int countType(WorkQueueResponse queue, String type) {
        return (int) queue.items().stream().filter((QueueEntry e) -> type.equals(e.type())).count();
    }

    private static String label(ClientComplianceDeadline d) {
        return d.getDescription() != null && !d.getDescription().isBlank() ? d.getDescription() : d.getDeadlineType();
    }

    private static List<Stage> toStages(List<DashboardRules.Stage> in) {
        return in.stream().map(s -> new Stage(s.status(), s.count(), s.value())).toList();
    }
}
