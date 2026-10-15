package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.internalaudit.application.internal.InternalAuditDashboardBuilder.*;
import za.co.handyflow.platform.internalaudit.domain.model.AnnualAuditPlan;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalAuditDashboardService {
    private final AnnualAuditPlanRepository plans;
    private final AuditPlanEntryRepository entries;
    private final AuditUniverseEntryRepository universe;
    private final AuditFindingRepository findings;
    private final AuditActionPlanRepository actions;

    /** The latest plan year drives the plan figures; findings and actions cover everything the tenant has. */
    @Transactional(readOnly = true)
    public Dashboard build(TenantId tenant, LocalDate today) {
        UUID t = tenant.getValue();
        AnnualAuditPlan plan = plans.findAllForTenant(t).stream().findFirst().orElse(null);
        List<PlanEntryIn> planEntries = plan == null ? List.of()
                : entries.findByPlan(t, plan.getId()).stream().map(e -> new PlanEntryIn(e.getUniverseEntryId(), e.getStatus())).toList();
        List<UniverseIn> uni = universe.findAllActive(t).stream().map(u -> new UniverseIn(u.getId(), u.getName())).toList();
        List<FindingIn> fs = findings.findAllForTenant(t).stream()
                .map(f -> new FindingIn(f.getId(), f.getTitle(), f.getSeverity(), f.getStatus(), f.getDueDate(),
                        f.getReopenedAt() != null || f.getPreviouslyClosedAt() != null)).toList();
        List<ActionIn> overdue = actions.findOverdue(t, today).stream()
                .map(a -> new ActionIn(a.getId(), a.getFindingId(), a.getDescription(), a.getDueDate())).toList();
        return InternalAuditDashboardBuilder.build(plan == null ? null : plan.getPlanYear(), plan == null ? null : plan.getStatus(),
                planEntries, uni, fs, overdue, today);
    }
}
