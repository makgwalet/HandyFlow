package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.internalaudit.application.internal.MyTasks.*;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditMyTasksService {
    private final AuditEngagementRepository engagements;
    private final AuditFindingRepository findings;
    private final AuditActionPlanRepository actions;
    private final AuditReviewNoteRepository notes;
    private final AuditWorkpaperFileRepository files;

    /**
     * What this person has to do. The database picks out only their own items; the names of the engagements, findings and
     * workpapers they belong to are then looked up for just those items.
     */
    @Transactional(readOnly = true)
    public Result forUser(TenantId tenant, UUID userId, boolean canValidate, LocalDate today) {
        UUID t = tenant.getValue();
        List<AuditActionPlan> myActions = new ArrayList<>(actions.findOpenOwnedBy(t, userId));
        if (canValidate) myActions.addAll(actions.findAwaitingValidationBy(t, userId));
        List<AuditFinding> myFindings = findings.findOpenOwnedBy(t, userId);
        List<AuditReviewNote> myNotes = notes.findWaitingOn(t, userId);

        // The finding an action belongs to gives its title and engagement.
        Map<UUID, AuditFinding> actionFindings = findings.findAllById(myActions.stream().map(AuditActionPlan::getFindingId).collect(Collectors.toSet()))
                .stream().filter(f -> t.equals(f.getTenantId())).collect(Collectors.toMap(AuditFinding::getId, f -> f));
        Map<UUID, AuditWorkpaperFile> noteFiles = files.findAllById(myNotes.stream().map(AuditReviewNote::getWorkpaperId).collect(Collectors.toSet()))
                .stream().filter(f -> t.equals(f.getTenantId())).collect(Collectors.toMap(AuditWorkpaperFile::getId, f -> f));

        Set<UUID> engagementIds = new HashSet<>();
        myFindings.forEach(f -> engagementIds.add(f.getEngagementId()));
        actionFindings.values().forEach(f -> engagementIds.add(f.getEngagementId()));
        myNotes.forEach(n -> engagementIds.add(n.getEngagementId()));
        Map<UUID, String> engName = engagements.findAllById(engagementIds).stream().filter(e -> t.equals(e.getTenantId()))
                .collect(Collectors.toMap(AuditEngagement::getId, AuditEngagement::getName));

        List<ActionIn> as = myActions.stream().map(a -> {
            AuditFinding f = actionFindings.get(a.getFindingId());
            UUID eng = f == null ? null : f.getEngagementId();
            return new ActionIn(a.getId(), eng, eng == null ? null : engName.get(eng), f == null ? null : f.getTitle(), a.getDescription(), a.getOwner(), a.getCompletedBy(), a.getDueDate(), a.getStatus());
        }).toList();
        List<FindingIn> fs = myFindings.stream().map(f -> new FindingIn(f.getId(), f.getEngagementId(), engName.get(f.getEngagementId()), f.getTitle(), f.getSeverity(), f.getOwner(), f.getDueDate(), f.getStatus())).toList();
        List<NoteIn> ns = myNotes.stream().map(n -> {
            AuditWorkpaperFile w = noteFiles.get(n.getWorkpaperId());
            return new NoteIn(n.getId(), n.getEngagementId(), engName.get(n.getEngagementId()), w == null ? null : w.getFileName(), n.getNote(), n.getRaisedBy(), w == null ? null : w.getPreparedBy(), n.getStatus());
        }).toList();
        return MyTasks.build(userId, canValidate, as, fs, ns, today);
    }
}
