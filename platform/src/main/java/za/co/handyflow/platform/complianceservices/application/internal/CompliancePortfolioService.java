package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PortfolioDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Early warning: which active clients show signs of trouble, and why. It reads what is already recorded and writes nothing.
 * It gives no score, and a client with no signs is not thereby compliant.
 */
@Service
@RequiredArgsConstructor
public class CompliancePortfolioService {

    private final ComplianceClientRepository clients;
    private final ClientComplianceDeadlineRepository deadlines;
    private final ClientComplianceDocumentRepository documents;
    private final ComplianceCaseRepository cases;
    private final ComplianceRegImpactRepository regImpacts;
    private final ComplianceAgreementRepository agreements;

    @Transactional(readOnly = true)
    public Summary earlyWarning(TenantId tenantId, LocalDate today) {
        Map<UUID, PortfolioRules.Facts> facts = new LinkedHashMap<>();
        for (ComplianceClient c : clients.findAll(tenantId, "ACTIVE", Pageable.unpaged()).getContent()) {
            PortfolioRules.Facts f = new PortfolioRules.Facts(c.getId(), c.getName());
            f.riskRating = c.getRiskRating();
            facts.put(c.getId(), f);
        }

        for (ClientComplianceDeadline d : deadlines.findPendingDueBy(tenantId, today.minusDays(1))) {
            PortfolioRules.Facts f = facts.get(d.getClientId());
            if (f == null) continue;
            f.overdueDeadlines++;
            f.oldestOverdueDays = (int) Math.max(f.oldestOverdueDays, ChronoUnit.DAYS.between(d.getDueDate(), today));
        }

        for (ClientComplianceDocument d : documents.findNeedingAttention(tenantId, today.plusDays(PortfolioRules.EXPIRY_DAYS))) {
            PortfolioRules.Facts f = facts.get(d.getClientId());
            if (f == null) continue;
            if ("VERIFIED".equals(d.getStatus())) {
                if (d.getExpiryDate().isBefore(today)) f.expiredDocuments++; else f.expiringDocuments++;
            } else if (ChronoUnit.DAYS.between(d.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), today) > PortfolioRules.WAITING_DAYS) {
                f.documentsWaiting++;
            }
        }
        for (ClientComplianceDocument d : documents.findRejectedSince(tenantId, today.minusDays(PortfolioRules.REJECTED_WINDOW_DAYS).atStartOfDay().toInstant(ZoneOffset.UTC))) {
            PortfolioRules.Facts f = facts.get(d.getClientId());
            if (f != null) f.rejectedRecently++;
        }

        for (ComplianceCase c : cases.findLiveForTenant(tenantId)) {
            PortfolioRules.Facts f = facts.get(c.getClientId());
            if (f == null) continue;
            if ("HIGH".equals(c.getSeverity()) || "CRITICAL".equals(c.getSeverity())) f.seriousCases++;
            if (c.getDueDate() != null && c.getDueDate().isBefore(today)) f.overdueCases++;
            if (ChronoUnit.DAYS.between(c.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), today) > PortfolioRules.STALLED_DAYS) f.stalledCases++;
        }

        List<ComplianceRegImpact> live = regImpacts.findLive(tenantId);
        for (ComplianceRegImpact i : live) {
            PortfolioRules.Facts f = facts.get(i.getClientId());
            if (f == null) continue;
            if (i.getDueDate() != null && i.getDueDate().isBefore(today)) f.overdueImpacts++; else f.openImpacts++;
        }

        Map<UUID, List<ComplianceAgreement>> byClient = new HashMap<>();
        for (ComplianceAgreement a : agreements.findAllForTenant(tenantId)) byClient.computeIfAbsent(a.getClientId(), k -> new ArrayList<>()).add(a);
        for (PortfolioRules.Facts f : facts.values()) {
            List<ComplianceAgreement> list = byClient.getOrDefault(f.clientId, List.of());
            List<String> statuses = new ArrayList<>();
            LocalDate earliest = null;
            for (ComplianceAgreement a : list) {
                statuses.add(a.getStatus());
                if ("ACTIVE".equals(a.getStatus()) && a.getEndDate() != null && (earliest == null || a.getEndDate().isBefore(earliest))) earliest = a.getEndDate();
            }
            String[] state = PortfolioRules.agreementState(statuses, earliest, today);
            f.agreement = state[0];
            f.agreementDays = Integer.parseInt(state[1]);
        }

        List<PortfolioRules.Entry> ranked = PortfolioRules.intervention(new ArrayList<>(facts.values()));
        List<ClientView> views = new ArrayList<>();
        int withHigh = 0;
        for (PortfolioRules.Entry e : ranked) {
            if (e.high() > 0) withHigh++;
            List<SignalView> signals = new ArrayList<>();
            for (PortfolioRules.Signal s : e.signals()) signals.add(new SignalView(s.code(), s.severity(), s.label(), s.detail(), s.tab()));
            views.add(new ClientView(e.clientId(), e.clientName(), e.riskRating(), e.high(), e.medium(), e.low(), signals));
        }
        return new Summary(today, facts.size(), views.size(), withHigh, views);
    }
}
