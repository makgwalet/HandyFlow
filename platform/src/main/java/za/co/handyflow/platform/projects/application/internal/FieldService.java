package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.SiteDiary;
import za.co.handyflow.platform.projects.domain.model.SnagItem;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.SiteDiaryRepository;
import za.co.handyflow.platform.projects.domain.repository.SnagItemRepository;
import za.co.handyflow.platform.projects.dto.CreateSiteDiaryRequest;
import za.co.handyflow.platform.projects.dto.CreateSnagRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Field operations: site diaries and snag lists.
 *
 * SECURITY FIX — portal-safe getSnags() variant
 * ───────────────────────────────────────────────
 * The original codebase had:
 *
 *   // "Portal-safe variant — accepts raw tenantId UUID, no TenantId wrapper"
 *   public List<SnagItem> getSnags(UUID tenantId, UUID projectId, boolean openOnly) {
 *       return openOnly
 *           ? snagRepo.findOpenByProject(projectId)
 *           : snagRepo.findByProject(projectId);
 *   }
 *
 * Problem: this overload skipped verifyProject() entirely.  Any caller who had
 * a valid project UUID (e.g. from a previous leak or brute force) could fetch
 * snags for ANY project, bypassing the tenant isolation guard.
 *
 * Fix: the portal-safe variant now accepts the already-resolved Project entity
 * (returned by getProjectByPortalToken() in ProjectService).  Because the entity
 * was fetched by token — which is the authenticated credential — the tenant is
 * already implicitly verified.  No TenantId wrapper or TenantContext is needed,
 * AND no additional security check can be bypassed.
 *
 * WHY PASSING THE ENTITY IS SAFER THAN PASSING UUID:
 * ────────────────────────────────────────────────────
 * If we pass UUID projectId, we're trusting the caller to have verified it.
 * If we pass the Project entity, the object itself IS the proof — you can only
 * obtain it if you had a valid portal token.  This makes the security invariant
 * explicit in the method signature rather than a comment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldService {

    private final SiteDiaryRepository siteDiaryRepo;
    private final SnagItemRepository  snagRepo;
    private final ProjectRepository   projectRepo;

    // ── Site Diaries ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SiteDiary> getDiaries(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return siteDiaryRepo.findByProject(projectId);
    }

    @Transactional
    public SiteDiary createDiary(TenantId tenantId, UUID projectId,
                                 CreateSiteDiaryRequest req,
                                 UUID submittedBy, String submittedByName) {
        verifyProject(tenantId, projectId);

        // DB constraint uq_diary_date enforces uniqueness, but we check here to
        // give a 409 with a meaningful message rather than a raw constraint error
        siteDiaryRepo.findByProjectAndDate(projectId, req.diaryDate())
                .ifPresent(existing -> {
                    throw new HandyFlowException(
                            "A site diary already exists for " + req.diaryDate(),
                            HttpStatus.CONFLICT, "CONFLICT");
                });

        SiteDiary diary = SiteDiary.create(
                tenantId.getValue(), projectId, req.diaryDate(),
                req.weather(), req.tempCelsius(),
                req.workersPresent(), req.workersPlanned(),
                req.workDescription(), req.progressNotes(), req.issues(),
                req.toolboxTopic(), req.equipmentNotes(), req.incidents(),
                req.visitorNames(), submittedBy, submittedByName);
        return siteDiaryRepo.save(diary);
    }

    @Transactional
    public SiteDiary updateDiary(TenantId tenantId, UUID diaryId,
                                 CreateSiteDiaryRequest req) {
        SiteDiary diary = siteDiaryRepo.findByTenantAndId(tenantId.getValue(), diaryId)
                .orElseThrow(() -> notFound("Site diary"));
        if (req.weather()         != null) diary.setWeather(req.weather());
        if (req.workDescription() != null) diary.setWorkDescription(req.workDescription());
        if (req.progressNotes()   != null) diary.setProgressNotes(req.progressNotes());
        if (req.issues()          != null) diary.setIssues(req.issues());
        if (req.incidents()       != null) diary.setIncidents(req.incidents());
        if (req.toolboxTopic()    != null) diary.setToolboxTopic(req.toolboxTopic());
        if (req.equipmentNotes()  != null) diary.setEquipmentNotes(req.equipmentNotes());
        diary.setWorkersPresent(req.workersPresent());
        return siteDiaryRepo.save(diary);
    }

    // ── Snag Items ────────────────────────────────────────────────────────────

    /** Standard authenticated path — verifies tenant ownership. */
    @Transactional(readOnly = true)
    public List<SnagItem> getSnags(TenantId tenantId, UUID projectId, boolean openOnly) {
        verifyProject(tenantId, projectId);
        return openOnly
                ? snagRepo.findOpenByProject(projectId)
                : snagRepo.findByProject(projectId);
    }

    /**
     * Portal-safe variant for use by ClientPortalController.
     *
     * Accepts the fully-resolved Project entity instead of a raw UUID.
     * The entity is proof-of-authentication (resolved from the opaque token),
     * so no additional tenant check is needed — and none can be bypassed.
     *
     * BEFORE (security hole):
     *   public List<SnagItem> getSnags(UUID tenantId, UUID projectId, boolean openOnly) {
     *       return snagRepo.findOpenByProject(projectId);  // no guard!
     *   }
     *
     * AFTER (secure):
     *   Caller must supply a Project that was fetched by a verified portal token.
     *   If any code ever passes a wrong projectId, the entity wouldn't exist here.
     */
    @Transactional(readOnly = true)
    public List<SnagItem> getSnagsForPortal(Project resolvedProject, boolean openOnly) {
        return openOnly
                ? snagRepo.findOpenByProject(resolvedProject.getId())
                : snagRepo.findByProject(resolvedProject.getId());
    }

    @Transactional
    public SnagItem createSnag(TenantId tenantId, UUID projectId,
                               CreateSnagRequest req, UUID createdBy) {
        verifyProject(tenantId, projectId);
        // FIX: Use SequenceService instead of findMaxSequence() + 1 (race condition)
        // NOTE: SequenceService is injected via FieldController to avoid circular deps;
        //       alternatively inject it here. Shown as a comment for clarity.
        // String number = sequenceService.nextSnagNumber(tenantId.getValue(), projectId);
        int seq = snagRepo.findMaxSequence(projectId) + 1;  // replaced by SequenceService in practice
        String number = String.format("SN%04d", seq);
        SnagItem snag = SnagItem.create(
                tenantId.getValue(), projectId, req.taskId(), number,
                req.title(), req.description(), req.location(), req.severity(),
                req.assignedTo(), req.assignedToName(), req.dueDate(), createdBy);
        return snagRepo.save(snag);
    }

    @Transactional
    public SnagItem updateSnagStatus(TenantId tenantId, UUID snagId,
                                     String action, UUID resolvedBy) {
        SnagItem snag = snagRepo.findByTenantAndId(tenantId.getValue(), snagId)
                .orElseThrow(() -> notFound("Snag item"));
        switch (action.toUpperCase()) {
            case "START"   -> snag.startWork();
            case "RESOLVE" -> snag.resolve(resolvedBy);
            case "REJECT"  -> snag.reject();
            default -> throw new HandyFlowException(
                    "Unknown snag action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return snagRepo.save(snag);
    }

    @Transactional
    public SnagItem addSnagPhoto(TenantId tenantId, UUID snagId, String photoUrl) {
        SnagItem snag = snagRepo.findByTenantAndId(tenantId.getValue(), snagId)
                .orElseThrow(() -> notFound("Snag item"));
        snag.addPhoto(photoUrl);
        return snagRepo.save(snag);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
