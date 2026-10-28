package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.internalaudit.application.internal.MyTasks.*;
import za.co.handyflow.platform.internalaudit.domain.model.AuditEngagement;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReviewNote;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperFile;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuditMyTasksService {
    private final AuditEngagementRepository engagements;
    private final AuditFindingRepository findings;
    private final AuditActionPlanRepository actions;
    private final AuditReviewNoteRepository notes;
    private final AuditWorkpaperFileRepository files;

    /** What this person has to do. Only their own items are read into the rules; nothing is returned about anyone else's work. */
    @Transactional(readOnly = true)
    public Result forUser(TenantId tenant, UUID userId, LocalDate today) {
        UUID t = tenant.getValue();
        Map<UUID, String> engName = new HashMap<>();
        for (AuditEngagement e : engagements.findAllForTenant(t)) engName.put(e.getId(), e.getName());

        var fs = findings.findAllForTenant(t);
        Map<UUID, String> findingTitle = new HashMap<>();
        Map<UUID, UUID> findingEngagement = new HashMap<>();
        fs.forEach(f -> { findingTitle.put(f.getId(), f.getTitle()); findingEngagement.put(f.getId(), f.getEngagementId()); });

        List<ActionIn> as = actions.findAllForTenant(t).stream().map(a -> {
            UUID eng = findingEngagement.get(a.getFindingId());
            return new ActionIn(a.getId(), eng, engName.get(eng), findingTitle.get(a.getFindingId()), a.getDescription(), a.getOwner(), a.getDueDate(), a.getStatus());
        }).toList();
        List<FindingIn> fin = fs.stream().map(f -> new FindingIn(f.getId(), f.getEngagementId(), engName.get(f.getEngagementId()), f.getTitle(), f.getSeverity(), f.getOwner(), f.getDueDate(), f.getStatus())).toList();

        // Workpapers are looked up only for notes that are still moving (open or answered), once each.
        Map<UUID, Optional<AuditWorkpaperFile>> wp = new HashMap<>();
        List<NoteIn> ns = new ArrayList<>();
        for (AuditReviewNote n : notes.findAllForTenant(t)) {
            if (!"OPEN".equals(n.getStatus()) && !"RESPONDED".equals(n.getStatus())) continue;
            Optional<AuditWorkpaperFile> f = wp.computeIfAbsent(n.getWorkpaperId(), id -> files.findByTenantAndId(t, id));
            ns.add(new NoteIn(n.getId(), n.getEngagementId(), engName.get(n.getEngagementId()), f.map(AuditWorkpaperFile::getFileName).orElse(null),
                    n.getNote(), n.getRaisedBy(), f.map(AuditWorkpaperFile::getPreparedBy).orElse(null), n.getStatus()));
        }
        return MyTasks.build(userId, as, fin, ns, today);
    }
}
