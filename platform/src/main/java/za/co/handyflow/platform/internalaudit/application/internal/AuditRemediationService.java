package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.RemediationBoard.*;
import za.co.handyflow.platform.internalaudit.domain.model.AuditActionPlan;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFinding;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuditRemediationService {
    private final AuditFindingRepository findings;
    private final AuditEngagementRepository engagements;
    private final AuditUniverseEntryRepository universe;
    private final AuditActionPlanRepository actions;
    private final UserRepository users;

    /** Every finding the company has, with its actions, as the findings and remediation screen needs them. */
    @Transactional(readOnly = true)
    public Board board(TenantId tenant, LocalDate today) {
        UUID t = tenant.getValue();
        Map<UUID, String> areas = new HashMap<>();
        universe.findAllActive(t).forEach(u -> areas.put(u.getId(), u.getName()));
        List<EngagementIn> engs = engagements.findAllForTenant(t).stream()
                .map(e -> new EngagementIn(e.getId(), e.getName(), areas.get(e.getUniverseEntryId()))).toList();
        List<AuditFinding> fs = findings.findAllForTenant(t);
        List<AuditActionPlan> as = actions.findAllForTenant(t);

        Set<UUID> people = new HashSet<>();
        fs.forEach(f -> { if (f.getOwner() != null) people.add(f.getOwner()); });
        as.forEach(a -> { if (a.getOwner() != null) people.add(a.getOwner()); });
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(people).forEach(u -> names.put(u.getId(), (u.getFirstName() + " " + u.getLastName()).trim()));

        return RemediationBoard.build(
                fs.stream().map(f -> new FindingIn(f.getId(), f.getEngagementId(), f.getTitle(), f.getDescription(), f.getRecommendation(),
                        f.getManagementResponse(), f.getSeverity(), f.getStatus(), f.getOwner(), f.getDueDate(), f.getCreatedAt(),
                        f.getReopenedAt() != null || f.getPreviouslyClosedAt() != null)).toList(),
                engs,
                as.stream().map(a -> new ActionIn(a.getId(), a.getFindingId(), a.getDescription(), a.getOwner(), a.getDueDate(), a.getStatus())).toList(),
                names, today);
    }
}
