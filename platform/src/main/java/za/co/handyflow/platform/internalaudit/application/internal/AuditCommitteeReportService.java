package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.AuditCommitteeReportBuilder.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditCommitteeReportService {
    private final AnnualAuditPlanRepository plans;
    private final AuditPlanEntryRepository entries;
    private final AuditUniverseEntryRepository universe;
    private final RiskAssessmentRepository risks;
    private final AuditFindingRepository findings;
    private final AuditActionPlanRepository actions;
    private final AuditEngagementRepository engagements;
    private final ApprovalFacade approvals;
    private final ControlExceptionFacade controls;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Report build(TenantId tenant, LocalDate today) {
        UUID t = tenant.getValue();
        var plan = plans.findAllForTenant(t).stream().findFirst().orElse(null);
        var planEntries = plan == null ? List.<PlanEntryIn>of()
                : entries.findByPlan(t, plan.getId()).stream().map(e -> new PlanEntryIn(e.getUniverseEntryId(), e.getPlannedQuarter(), e.getStatus())).toList();
        var uni = universe.findAllActive(t).stream().map(u -> new UniverseIn(u.getId(), u.getName(),
                risks.findLatestForUniverseEntry(t, u.getId()).map(r -> r.getFinalAuditRisk()).orElse(null))).toList();
        var fs = findings.findAllForTenant(t).stream().map(f -> new FindingIn(f.getId(), f.getTitle(), f.getSeverity(), f.getStatus(), f.getDueDate())).toList();

        Map<UUID, String> names = new HashMap<>();
        var late = actions.findOverdue(t, today).stream().map(a -> new ActionIn(a.getId(), a.getFindingId(), a.getDescription(), a.getDueDate(),
                a.getOwner() == null ? null : names.computeIfAbsent(a.getOwner(), id -> users.findByIdAndTenantId(id, tenant)
                        .map(u -> (u.getFirstName() + " " + u.getLastName()).trim()).orElse("Unknown")))).toList();

        var reports = engagements.findAllForTenant(t).stream().map(e -> {
            var latest = approvals.getLatestRequestForEntity(tenant, "internalaudit", "ENGAGEMENT_REPORT", e.getId()).orElse(null);
            return new ReportIn(e.getName(), e.getStatus(), latest == null ? null : latest.status(), latest == null ? null : latest.completedAt());
        }).toList();

        var c = controls.summary(tenant, today);
        return AuditCommitteeReportBuilder.build(today, plan == null ? null : plan.getPlanYear(), plan == null ? null : plan.getStatus(), planEntries, uni, fs, late, reports,
                new ControlFigures(c.open(), c.unrated(), c.highOrCritical(), c.overdue(), c.followUpsDue(), c.reopenedEver()));
    }
}
