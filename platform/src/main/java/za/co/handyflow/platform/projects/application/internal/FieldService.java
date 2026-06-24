package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

        // Enforce unique-per-date (DB constraint: uq_diary_date)
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
        if (req.weather()          != null) diary.setWeather(req.weather());
        if (req.workDescription()  != null) diary.setWorkDescription(req.workDescription());
        if (req.progressNotes()    != null) diary.setProgressNotes(req.progressNotes());
        if (req.issues()           != null) diary.setIssues(req.issues());
        if (req.incidents()        != null) diary.setIncidents(req.incidents());
        if (req.toolboxTopic()     != null) diary.setToolboxTopic(req.toolboxTopic());
        if (req.equipmentNotes()   != null) diary.setEquipmentNotes(req.equipmentNotes());
        diary.setWorkersPresent(req.workersPresent());
        return siteDiaryRepo.save(diary);
    }

    // ── Snag Items ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SnagItem> getSnags(TenantId tenantId, UUID projectId, boolean openOnly) {
        verifyProject(tenantId, projectId);
        return openOnly
                ? snagRepo.findOpenByProject(projectId)
                : snagRepo.findByProject(projectId);
    }

    @Transactional
    public SnagItem createSnag(TenantId tenantId, UUID projectId,
                               CreateSnagRequest req, UUID createdBy) {
        verifyProject(tenantId, projectId);
        int seq = snagRepo.findMaxSequence(projectId) + 1;
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
            default -> throw new HandyFlowException("Unknown action: " + action,
                    HttpStatus.BAD_REQUEST, "BAD_REQUEST");
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

    /** Portal-safe variant — accepts raw tenantId UUID, no TenantId wrapper */
    @Transactional(readOnly = true)
    public java.util.List<SnagItem> getSnags(UUID tenantId, UUID projectId, boolean openOnly) {
        return openOnly
                ? snagRepo.findOpenByProject(projectId)
                : snagRepo.findByProject(projectId);
    }


    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}

