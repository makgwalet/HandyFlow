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
    public Board board(TenantId tenant, LocalDate today) { return board(tenant, today, null); }

    /** As above, limited to one engagement when `engagementId` is given; the database does the limiting, not the screen. */
    @Transactional(readOnly = true)
    public Board board(TenantId tenant, LocalDate today, UUID engagementId) {
        UUID t = tenant.getValue();
        Map<UUID, String> areas = new HashMap<>();
        universe.findAllActive(t).forEach(u -> areas.put(u.getId(), u.getName()));
        List<EngagementIn> engs = engagements.findAllForTenant(t).stream().filter(e -> engagementId == null || engagementId.equals(e.getId()))
                .map(e -> new EngagementIn(e.getId(), e.getName(), areas.get(e.getUniverseEntryId()))).toList();
        List<AuditFinding> fs = engagementId == null ? findings.findAllForTenant(t) : findings.findByEngagement(t, engagementId);
        List<AuditActionPlan> as = engagementId == null ? actions.findAllForTenant(t)
                : fs.isEmpty() ? List.of() : actions.findByFindingIds(t, fs.stream().map(AuditFinding::getId).toList());

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
