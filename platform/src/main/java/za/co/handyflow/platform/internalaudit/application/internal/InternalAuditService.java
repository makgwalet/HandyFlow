package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.accounting.domain.model.AccJournalEntry;
import za.co.handyflow.platform.accounting.domain.repository.AccJournalEntryRepository;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.domain.model.ApprovalRule;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ChainEntryInput;
import za.co.handyflow.platform.internalaudit.domain.model.AnnualAuditPlan;
import za.co.handyflow.platform.internalaudit.domain.model.AuditEngagement;
import za.co.handyflow.platform.internalaudit.domain.model.AuditPlanEntry;
import za.co.handyflow.platform.internalaudit.domain.model.AuditUniverseEntry;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperFile;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperFolder;
import za.co.handyflow.platform.internalaudit.domain.model.EngagementAssignment;
import za.co.handyflow.platform.internalaudit.domain.model.RiskAssessment;
import za.co.handyflow.platform.internalaudit.domain.model.SpecificMateriality;
import za.co.handyflow.platform.internalaudit.domain.model.SamplingPlan;
import za.co.handyflow.platform.internalaudit.domain.model.SampleItem;
import za.co.handyflow.platform.internalaudit.domain.model.AuditTest;
import za.co.handyflow.platform.internalaudit.domain.model.AuditException;
import za.co.handyflow.platform.internalaudit.domain.repository.AnnualAuditPlanRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditEngagementRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditPlanEntryRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditUniverseEntryRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditWorkpaperFileRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditWorkpaperFolderRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.EngagementAssignmentRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.RiskAssessmentRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.SpecificMaterialityRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.SamplingPlanRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.SampleItemRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditTestRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditExceptionRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFinding;
import za.co.handyflow.platform.internalaudit.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Internal Audit Phase 1, per the design agreed with the product owner:
 * Audit Universe, hybrid risk scoring, Annual Audit Plan, Engagement
 * shell with engagement-scoped role assignments. See each entity's own
 * class comment for the fuller design reasoning behind its specific
 * shape. Phases 2–4 (workpapers, GL sampling, findings/remediation,
 * report sign-off) are deliberately not started — this service only
 * covers what Phase 1 scoped.
 */
@Service
@RequiredArgsConstructor
public class InternalAuditService {

    private final AuditUniverseEntryRepository universeRepo;
    private final RiskAssessmentRepository riskRepo;
    private final AnnualAuditPlanRepository planRepo;
    private final AuditPlanEntryRepository planEntryRepo;
    private final AuditEngagementRepository engagementRepo;
    private final EngagementAssignmentRepository assignmentRepo;
    private final UserRepository userRepo;
    private final SpecificMaterialityRepository specificMaterialityRepo;
    private final AuditWorkpaperFolderRepository workpaperFolderRepo;
    private final AuditWorkpaperFileRepository workpaperFileRepo;
    private final SamplingPlanRepository samplingPlanRepo;
    private final SampleItemRepository sampleItemRepo;
    private final AuditTestRepository auditTestRepo;
    private final AuditExceptionRepository auditExceptionRepo;
    private final AccJournalEntryRepository journalEntryRepo;
    private final AuditFindingRepository findingRepo;
    private final ApprovalFacade approvalFacade;

    // ── Universe ─────────────────────────────────────────────────────────────

    @Transactional
    public UniverseEntryResponse createUniverseEntry(TenantId tenantId, CreateUniverseEntryRequest req, UUID createdBy) {
        AuditUniverseEntry e = AuditUniverseEntry.create(tenantId.getValue(), req.name(), req.description(),
                req.processArea(), req.glAccountGroup(), createdBy);
        universeRepo.save(e);
        return toUniverseResponse(e);
    }

    @Transactional
    public UniverseEntryResponse updateUniverseEntry(TenantId tenantId, UUID id, CreateUniverseEntryRequest req) {
        AuditUniverseEntry e = universeRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditUniverseEntry", id.toString()));
        e.update(req.name(), req.description(), req.processArea(), req.glAccountGroup());
        universeRepo.save(e);
        return toUniverseResponse(e);
    }

    @Transactional
    public void deactivateUniverseEntry(TenantId tenantId, UUID id) {
        AuditUniverseEntry e = universeRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditUniverseEntry", id.toString()));
        e.deactivate();
        universeRepo.save(e);
    }

    @Transactional(readOnly = true)
    public List<UniverseEntryResponse> getUniverse(TenantId tenantId) {
        return universeRepo.findAllActive(tenantId.getValue()).stream().map(this::toUniverseResponse).toList();
    }

    private UniverseEntryResponse toUniverseResponse(AuditUniverseEntry e) {
        String currentRisk = riskRepo.findLatestForUniverseEntry(e.getTenantId(), e.getId())
                .map(RiskAssessment::getFinalAuditRisk).orElse(null);
        return new UniverseEntryResponse(e.getId(), e.getName(), e.getDescription(), e.getProcessArea(),
                e.getGlAccountGroup(), e.getLastAuditDate(), e.isActive(), e.getCreatedAt(), currentRisk);
    }

    // ── Risk Assessment ──────────────────────────────────────────────────────

    @Transactional
    public RiskAssessmentResponse createRiskAssessment(TenantId tenantId, UUID universeEntryId,
                                                        CreateRiskAssessmentRequest req, UUID assessedBy) {
        AuditUniverseEntry universeEntry = universeRepo.findByTenantAndId(tenantId.getValue(), universeEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditUniverseEntry", universeEntryId.toString()));

        RiskAssessment r = RiskAssessment.create(tenantId.getValue(), universeEntryId,
                req.inherentRiskScore(), req.controlRiskScore(), req.historicalFindingsScore(),
                universeEntry.timeSinceLastAuditScore(), // the one system-calculated input, per the agreed design
                req.businessRegulatoryImpactScore(), assessedBy);
        riskRepo.save(r);
        return toRiskResponse(r);
    }

    @Transactional
    public RiskAssessmentResponse overrideRisk(TenantId tenantId, UUID riskAssessmentId,
                                               OverrideRiskRequest req, UUID approvedBy) {
        RiskAssessment r = riskRepo.findByTenantAndId(tenantId.getValue(), riskAssessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("RiskAssessment", riskAssessmentId.toString()));
        try {
            r.override(req.finalAuditRisk(), req.reason(), approvedBy);
        } catch (IllegalArgumentException ex) {
            throw new HandyFlowException(ex.getMessage(), HttpStatus.BAD_REQUEST, "OVERRIDE_REASON_REQUIRED");
        }
        riskRepo.save(r);
        return toRiskResponse(r);
    }

    @Transactional(readOnly = true)
    public List<RiskAssessmentResponse> getRiskHistory(TenantId tenantId, UUID universeEntryId) {
        return riskRepo.findByUniverseEntry(tenantId.getValue(), universeEntryId).stream()
                .map(this::toRiskResponse).toList();
    }

    private RiskAssessmentResponse toRiskResponse(RiskAssessment r) {
        return new RiskAssessmentResponse(r.getId(), r.getUniverseEntryId(),
                r.getInherentRiskScore(), r.getControlRiskScore(), r.getHistoricalFindingsScore(),
                r.getTimeSinceLastAuditScore(), r.getBusinessRegulatoryImpactScore(),
                r.getSystemCalculatedRisk(), r.getFinalAuditRisk(),
                r.getOverrideReason(), r.getOverrideApprovedBy(), r.getAssessedBy(), r.getAssessedAt());
    }

    // ── Annual Plan ──────────────────────────────────────────────────────────

    @Transactional
    public AnnualPlanResponse createAnnualPlan(TenantId tenantId, CreateAnnualPlanRequest req, UUID createdBy) {
        AnnualAuditPlan p = AnnualAuditPlan.create(tenantId.getValue(), req.planYear(), createdBy);
        planRepo.save(p);
        return toPlanResponse(p);
    }

    @Transactional
    public AnnualPlanResponse approveAnnualPlan(TenantId tenantId, UUID planId, UUID approvedBy) {
        AnnualAuditPlan p = planRepo.findByTenantAndId(tenantId.getValue(), planId)
                .orElseThrow(() -> new ResourceNotFoundException("AnnualAuditPlan", planId.toString()));
        try {
            p.approve(approvedBy);
        } catch (IllegalStateException ex) {
            throw new HandyFlowException(ex.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        planRepo.save(p);
        return toPlanResponse(p);
    }

    @Transactional(readOnly = true)
    public List<AnnualPlanResponse> getAnnualPlans(TenantId tenantId) {
        return planRepo.findAllForTenant(tenantId.getValue()).stream().map(this::toPlanResponse).toList();
    }

    @Transactional(readOnly = true)
    public AnnualPlanResponse getAnnualPlan(TenantId tenantId, UUID planId) {
        AnnualAuditPlan p = planRepo.findByTenantAndId(tenantId.getValue(), planId)
                .orElseThrow(() -> new ResourceNotFoundException("AnnualAuditPlan", planId.toString()));
        return toPlanResponse(p);
    }

    @Transactional
    public PlanEntryResponse addPlanEntry(TenantId tenantId, UUID planId, AddPlanEntryRequest req) {
        planRepo.findByTenantAndId(tenantId.getValue(), planId)
                .orElseThrow(() -> new ResourceNotFoundException("AnnualAuditPlan", planId.toString()));
        AuditUniverseEntry universeEntry = universeRepo.findByTenantAndId(tenantId.getValue(), req.universeEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("AuditUniverseEntry", req.universeEntryId().toString()));

        AuditPlanEntry entry = AuditPlanEntry.create(tenantId.getValue(), planId, req.universeEntryId(),
                req.riskAssessmentId(), req.plannedQuarter(), req.rationale());
        planEntryRepo.save(entry);
        return toPlanEntryResponse(entry, universeEntry.getName());
    }

    private AnnualPlanResponse toPlanResponse(AnnualAuditPlan p) {
        List<PlanEntryResponse> entries = planEntryRepo.findByPlan(p.getTenantId(), p.getId()).stream()
                .map(e -> {
                    String universeEntryName = universeRepo.findByTenantAndId(p.getTenantId(), e.getUniverseEntryId())
                            .map(AuditUniverseEntry::getName).orElse("Unknown");
                    return toPlanEntryResponse(e, universeEntryName);
                }).toList();
        return new AnnualPlanResponse(p.getId(), p.getPlanYear(), p.getStatus(),
                p.getApprovedBy(), p.getApprovedAt(), p.getCreatedBy(), p.getCreatedAt(), entries);
    }

    private PlanEntryResponse toPlanEntryResponse(AuditPlanEntry e, String universeEntryName) {
        String riskLevel = e.getRiskAssessmentId() != null
                ? riskRepo.findByTenantAndId(e.getTenantId(), e.getRiskAssessmentId())
                        .map(RiskAssessment::getFinalAuditRisk).orElse(null)
                : null;
        return new PlanEntryResponse(e.getId(), e.getUniverseEntryId(), universeEntryName,
                e.getRiskAssessmentId(), riskLevel, e.getPlannedQuarter(), e.getRationale(),
                e.getStatus(), e.getCreatedAt());
    }

    // ── Engagement ───────────────────────────────────────────────────────────

    @Transactional
    public EngagementResponse createEngagement(TenantId tenantId, CreateEngagementRequest req, UUID createdBy) {
        universeRepo.findByTenantAndId(tenantId.getValue(), req.universeEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("AuditUniverseEntry", req.universeEntryId().toString()));
        AuditEngagement e = AuditEngagement.create(tenantId.getValue(), req.planEntryId(), req.universeEntryId(),
                req.name(), req.startDate(), req.endDate(), createdBy);
        engagementRepo.save(e);
        return toEngagementResponse(e);
    }

    @Transactional(readOnly = true)
    public List<EngagementResponse> getEngagements(TenantId tenantId) {
        return engagementRepo.findAllForTenant(tenantId.getValue()).stream().map(this::toEngagementResponse).toList();
    }

    @Transactional
    public EngagementAssignmentResponse assignRole(TenantId tenantId, UUID engagementId,
                                                    AssignEngagementRoleRequest req, UUID assignedBy) {
        engagementRepo.findByTenantAndId(tenantId.getValue(), engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));
        EngagementAssignment.Role role;
        try {
            role = EngagementAssignment.Role.valueOf(req.role());
        } catch (IllegalArgumentException ex) {
            throw new HandyFlowException("Unknown engagement role: " + req.role(), HttpStatus.BAD_REQUEST, "INVALID_ROLE");
        }
        EngagementAssignment a = EngagementAssignment.create(tenantId.getValue(), engagementId, req.userId(), role, assignedBy);
        assignmentRepo.save(a);
        return toAssignmentResponse(a);
    }

    private EngagementResponse toEngagementResponse(AuditEngagement e) {
        String universeEntryName = universeRepo.findByTenantAndId(e.getTenantId(), e.getUniverseEntryId())
                .map(AuditUniverseEntry::getName).orElse("Unknown");
        List<EngagementAssignmentResponse> assignments = assignmentRepo.findByEngagement(e.getTenantId(), e.getId())
                .stream().map(this::toAssignmentResponse).toList();
        List<SpecificMaterialityResponse> specificMateriality = specificMaterialityRepo.findByEngagement(e.getTenantId(), e.getId())
                .stream().map(m -> new SpecificMaterialityResponse(m.getId(), m.getAccountOrGlSegment(), m.getThreshold())).toList();
        return new EngagementResponse(e.getId(), e.getPlanEntryId(), e.getUniverseEntryId(), universeEntryName,
                e.getName(), e.getStatus(), e.getStartDate(), e.getEndDate(), e.getCreatedBy(), e.getCreatedAt(),
                assignments, e.getObjectives(), e.getScope(), e.getAuditCriteria(),
                e.getOverallMateriality(), e.getPerformanceMateriality(), e.getClearlyTrivialThreshold(),
                specificMateriality);
    }

    private EngagementAssignmentResponse toAssignmentResponse(EngagementAssignment a) {
        String userName = userRepo.findById(a.getUserId())
                .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .orElse("Unknown");
        return new EngagementAssignmentResponse(a.getId(), a.getUserId(), userName, a.getRole(),
                a.getAssignedBy(), a.getAssignedAt());
    }

    // ── Phase 2: Planning detail + Materiality ──────────────────────────────────

    @Transactional
    public EngagementResponse updatePlanning(TenantId tenantId, UUID engagementId, UpdatePlanningRequest req) {
        AuditEngagement e = engagementRepo.findByTenantAndId(tenantId.getValue(), engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));
        e.updatePlanning(req.objectives(), req.scope(), req.auditCriteria(),
                req.overallMateriality(), req.performanceMateriality(), req.clearlyTrivialThreshold());
        engagementRepo.save(e);
        return toEngagementResponse(e);
    }

    @Transactional
    public SpecificMaterialityResponse addSpecificMateriality(TenantId tenantId, UUID engagementId,
                                                               AddSpecificMaterialityRequest req) {
        engagementRepo.findByTenantAndId(tenantId.getValue(), engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));
        SpecificMateriality m = SpecificMateriality.create(tenantId.getValue(), engagementId,
                req.accountOrGlSegment(), req.threshold());
        specificMaterialityRepo.save(m);
        return new SpecificMaterialityResponse(m.getId(), m.getAccountOrGlSegment(), m.getThreshold());
    }

    // ── Phase 2: Workpapers ──────────────────────────────────────────────────────
    // Direct structural mirror of AccWorkpaperService (Accountant module)
    // — same file-type/size validation, same versioning-on-reupload
    // logic. See AuditWorkpaperFile's own class comment for the fuller
    // reasoning.

    private static final long MAX_WORKPAPER_FILE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_WORKPAPER_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    @Transactional
    public WorkpaperFolderResponse createWorkpaperFolder(TenantId tenantId, UUID engagementId,
                                                          CreateWorkpaperFolderRequest req) {
        engagementRepo.findByTenantAndId(tenantId.getValue(), engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));
        AuditWorkpaperFolder f = AuditWorkpaperFolder.create(tenantId.getValue(), engagementId,
                req.parentId(), req.name(), req.folderType(), req.sortOrder());
        workpaperFolderRepo.save(f);
        return toFolderResponse(f);
    }

    @Transactional(readOnly = true)
    public List<WorkpaperFolderResponse> getWorkpaperFolders(TenantId tenantId, UUID engagementId) {
        return workpaperFolderRepo.findByEngagement(tenantId.getValue(), engagementId).stream()
                .map(this::toFolderResponse).toList();
    }

    @Transactional
    public WorkpaperFileResponse uploadWorkpaperFile(TenantId tenantId, UUID engagementId,
                                                      UploadWorkpaperFileRequest req) {
        engagementRepo.findByTenantAndId(tenantId.getValue(), engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));
        AuditWorkpaperFolder folder = workpaperFolderRepo.findByTenantAndId(tenantId.getValue(), req.folderId())
                .orElseThrow(() -> new HandyFlowException("Folder not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!folder.getEngagementId().equals(engagementId)) {
            throw new HandyFlowException("Folder not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }

        String mimeType = req.mimeType() != null ? req.mimeType() : "application/octet-stream";
        if (!ALLOWED_WORKPAPER_TYPES.contains(mimeType)) {
            throw new HandyFlowException(
                    "Unsupported file type — please upload a PDF, JPG, PNG, Word, or Excel document",
                    HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE");
        }
        long approxDecodedBytes = (req.fileContentBase64().length() * 3L) / 4;
        if (approxDecodedBytes > MAX_WORKPAPER_FILE_BYTES) {
            throw new HandyFlowException(
                    "File is too large — maximum is " + (MAX_WORKPAPER_FILE_BYTES / (1024 * 1024)) + "MB",
                    HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }

        AuditWorkpaperFile previous = workpaperFileRepo
                .findCurrentVersionByName(tenantId.getValue(), req.folderId(), req.fileName()).orElse(null);
        int nextVersion = previous != null ? previous.getVersionNumber() + 1 : 1;

        AuditWorkpaperFile file = AuditWorkpaperFile.create(tenantId.getValue(), engagementId, req.folderId(),
                req.fileName(), mimeType, req.fileSizeBytes(), req.fileContentBase64(), nextVersion);
        workpaperFileRepo.save(file);

        if (previous != null) {
            previous.markSuperseded(file.getId());
            workpaperFileRepo.save(previous);
        }
        return toFileResponse(file);
    }

    @Transactional(readOnly = true)
    public List<WorkpaperFileResponse> getWorkpaperFiles(TenantId tenantId, UUID folderId) {
        return workpaperFileRepo.findActiveByFolder(tenantId.getValue(), folderId).stream()
                .map(this::toFileResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkpaperFileResponse> getDeletedWorkpaperFiles(TenantId tenantId, UUID folderId) {
        return workpaperFileRepo.findDeletedByFolder(tenantId.getValue(), folderId).stream()
                .map(this::toFileResponse).toList();
    }

    /**
     * action is one of PREPARE | REVIEW | SIGN_OFF | REOPEN — maps
     * directly onto AuditWorkpaperFile's own domain methods rather than
     * letting the caller set an arbitrary reviewStatus string. Who is
     * allowed to review/sign off (segregation of duties — a preparer
     * shouldn't review their own work) is a Phase 3+ enforcement point
     * once EngagementAssignment roles are checked here; not yet wired
     * in this pass.
     */
    @Transactional
    public WorkpaperFileResponse updateWorkpaperFileStatus(TenantId tenantId, UUID fileId,
                                                            UpdateWorkpaperStatusRequest req, UUID actingUserId) {
        AuditWorkpaperFile file = workpaperFileRepo.findByTenantAndId(tenantId.getValue(), fileId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkpaperFile", fileId.toString()));
        try {
            switch (req.action()) {
                case "PREPARE" -> file.markPrepared(actingUserId);
                case "REVIEW" -> file.markReviewed(actingUserId);
                case "SIGN_OFF" -> file.signOff(actingUserId);
                case "REOPEN" -> file.reopen();
                default -> throw new HandyFlowException("Unknown action: " + req.action(), HttpStatus.BAD_REQUEST, "INVALID_ACTION");
            }
        } catch (IllegalStateException ex) {
            throw new HandyFlowException(ex.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        workpaperFileRepo.save(file);
        return toFileResponse(file);
    }

    @Transactional
    public void deleteWorkpaperFile(TenantId tenantId, UUID fileId) {
        AuditWorkpaperFile file = workpaperFileRepo.findByTenantAndId(tenantId.getValue(), fileId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkpaperFile", fileId.toString()));
        file.softDelete();
        workpaperFileRepo.save(file);
    }

    @Transactional
    public WorkpaperFileResponse restoreWorkpaperFile(TenantId tenantId, UUID fileId) {
        AuditWorkpaperFile file = workpaperFileRepo.findByTenantAndId(tenantId.getValue(), fileId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkpaperFile", fileId.toString()));
        file.restore();
        workpaperFileRepo.save(file);
        return toFileResponse(file);
    }

    private WorkpaperFolderResponse toFolderResponse(AuditWorkpaperFolder f) {
        return new WorkpaperFolderResponse(f.getId(), f.getName(), f.getParentId(), f.getFolderType(), f.getSortOrder());
    }

    private WorkpaperFileResponse toFileResponse(AuditWorkpaperFile f) {
        return new WorkpaperFileResponse(f.getId(), f.getFolderId(), f.getFileName(), f.getMimeType(), f.getFileSizeBytes(),
                f.getReviewStatus(), f.getPreparedBy(), f.getPreparedAt(), f.getReviewedBy(), f.getReviewedAt(),
                f.getSignedOffBy(), f.getSignedOffAt(), f.getVersionNumber(), f.getSupersededBy(), f.getCreatedAt());
    }

    // ── Phase 3: GL Sampling & Testing ──────────────────────────────────────────
    // journalEntryRepo.findPostedInRange() already existed (used
    // internally elsewhere in Accounting) — no new query needed on that
    // side. Drawing the sample uses simple random selection
    // (Collections.shuffle) rather than a cryptographic RNG — audit
    // sample selection needs to be unbiased, not adversarially
    // unpredictable, so java.util.Random's default seeding is
    // appropriate here and matches how "Random" selection is described
    // in the agreed design (auditor-judgment sample SIZE, system does
    // the physical random draw).

    @Transactional
    public SamplingPlanResponse createSamplingPlan(TenantId tenantId, UUID engagementId,
                                                    CreateSamplingPlanRequest req, UUID preparedBy) {
        engagementRepo.findByTenantAndId(tenantId.getValue(), engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));

        List<AccJournalEntry> population = journalEntryRepo.findPostedInRange(
                tenantId, req.samplePeriodFrom(), req.samplePeriodTo());
        if (req.sampleSize() > population.size()) {
            throw new HandyFlowException(
                    "Sample size (" + req.sampleSize() + ") cannot exceed the population (" + population.size() + " posted journal entries in this period)",
                    HttpStatus.BAD_REQUEST, "SAMPLE_SIZE_EXCEEDS_POPULATION");
        }
        java.math.BigDecimal populationValue = population.stream()
                .map(AccJournalEntry::getTotalDebit).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        SamplingPlan plan = SamplingPlan.create(tenantId.getValue(), engagementId, population.size(), populationValue,
                req.samplingObjective(), req.riskLevel(), req.expectedErrorRate(), req.tolerableErrorRate(),
                req.sampleSize(), req.selectionMethod() != null ? req.selectionMethod() : "RANDOM",
                req.samplePeriodFrom(), req.samplePeriodTo(), req.exclusions(), req.rationale(), preparedBy);
        samplingPlanRepo.save(plan);

        // Draw the sample immediately — the plan and its drawn items are
        // one atomic unit; there's no meaningful "plan exists with no
        // sample drawn yet" state worth modeling separately in V1.
        List<AccJournalEntry> shuffled = new ArrayList<>(population);
        Collections.shuffle(shuffled);
        for (AccJournalEntry je : shuffled.subList(0, req.sampleSize())) {
            SampleItem item = SampleItem.create(tenantId.getValue(), plan.getId(), je.getId(),
                    je.getEntryNumber(), je.getEntryDate(), je.getTotalDebit());
            sampleItemRepo.save(item);
        }

        return toSamplingPlanResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<SamplingPlanResponse> getSamplingPlans(TenantId tenantId, UUID engagementId) {
        return samplingPlanRepo.findByEngagement(tenantId.getValue(), engagementId).stream()
                .map(this::toSamplingPlanResponse).toList();
    }

    @Transactional
    public SamplingPlanResponse reviewSamplingPlan(TenantId tenantId, UUID planId, UUID reviewedBy) {
        SamplingPlan plan = samplingPlanRepo.findByTenantAndId(tenantId.getValue(), planId)
                .orElseThrow(() -> new ResourceNotFoundException("SamplingPlan", planId.toString()));
        plan.review(reviewedBy);
        plan.finalizePlan();
        samplingPlanRepo.save(plan);
        return toSamplingPlanResponse(plan);
    }

    @Transactional
    public AuditTestResponse createTest(TenantId tenantId, UUID sampleItemId, CreateAuditTestRequest req) {
        sampleItemRepo.findByTenantAndId(tenantId.getValue(), sampleItemId)
                .orElseThrow(() -> new ResourceNotFoundException("SampleItem", sampleItemId.toString()));
        AuditTest test = AuditTest.create(tenantId.getValue(), sampleItemId, req.procedure());
        auditTestRepo.save(test);
        return toTestResponse(test);
    }

    @Transactional
    public AuditTestResponse recordTestResult(TenantId tenantId, UUID testId, RecordTestResultRequest req, UUID testedBy) {
        AuditTest test = auditTestRepo.findByTenantAndId(tenantId.getValue(), testId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditTest", testId.toString()));
        test.recordResult(req.result(), req.notes(), testedBy);
        auditTestRepo.save(test);
        return toTestResponse(test);
    }

    @Transactional
    public AuditExceptionResponse raiseException(TenantId tenantId, UUID testId, RaiseExceptionRequest req, UUID raisedBy) {
        auditTestRepo.findByTenantAndId(tenantId.getValue(), testId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditTest", testId.toString()));
        AuditException ex = AuditException.raise(tenantId.getValue(), testId, req.description(), req.severity(), raisedBy);
        auditExceptionRepo.save(ex);
        return toExceptionResponse(ex);
    }

    @Transactional
    public AuditExceptionResponse dismissException(TenantId tenantId, UUID exceptionId, DismissExceptionRequest req) {
        AuditException ex = auditExceptionRepo.findByTenantAndId(tenantId.getValue(), exceptionId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditException", exceptionId.toString()));
        ex.dismiss(req.resolutionNotes());
        auditExceptionRepo.save(ex);
        return toExceptionResponse(ex);
    }

    @Transactional(readOnly = true)
    public List<AuditExceptionResponse> getExceptionsForEngagement(TenantId tenantId, UUID engagementId) {
        return auditExceptionRepo.findByEngagement(tenantId.getValue(), engagementId).stream()
                .map(this::toExceptionResponse).toList();
    }

    private SamplingPlanResponse toSamplingPlanResponse(SamplingPlan p) {
        List<SampleItemResponse> items = sampleItemRepo.findByPlan(p.getTenantId(), p.getId()).stream()
                .map(this::toSampleItemResponse).toList();
        return new SamplingPlanResponse(p.getId(), p.getEngagementId(), p.getPopulation(), p.getPopulationValue(),
                p.getSamplingObjective(), p.getSamplingMethod(), p.getRiskLevel(), p.getConfidenceLevel(),
                p.getExpectedErrorRate(), p.getTolerableErrorRate(), p.getSampleSize(), p.getSelectionMethod(),
                p.getSamplePeriodFrom(), p.getSamplePeriodTo(), p.getExclusions(), p.getRationale(),
                p.getPreparedBy(), p.getReviewedBy(), p.getStatus(), p.getCreatedAt(), items);
    }

    private SampleItemResponse toSampleItemResponse(SampleItem s) {
        List<AuditTestResponse> tests = auditTestRepo.findBySampleItem(s.getTenantId(), s.getId()).stream()
                .map(this::toTestResponse).toList();
        return new SampleItemResponse(s.getId(), s.getJournalEntryId(), s.getEntryNumberSnapshot(),
                s.getEntryDateSnapshot(), s.getAmountSnapshot(), s.getNotes(), s.getSelectedAt(), tests);
    }

    private AuditTestResponse toTestResponse(AuditTest t) {
        List<AuditExceptionResponse> exceptions = auditExceptionRepo.findByTest(t.getTenantId(), t.getId()).stream()
                .map(this::toExceptionResponse).toList();
        return new AuditTestResponse(t.getId(), t.getSampleItemId(), t.getProcedure(), t.getResult(), t.getNotes(),
                t.getTestedBy(), t.getTestedAt(), exceptions);
    }

    private AuditExceptionResponse toExceptionResponse(AuditException e) {
        return new AuditExceptionResponse(e.getId(), e.getAuditTestId(), e.getDescription(), e.getSeverity(),
                e.getStatus(), e.getRaisedBy(), e.getRaisedAt(), e.getResolutionNotes());
    }

    // ── Phase 4: Findings, Remediation, Report Sign-off ─────────────────────────

    @Transactional
    public FindingResponse createFinding(TenantId tenantId, UUID engagementId, CreateFindingRequest req, UUID createdBy) {
        engagementRepo.findByTenantAndId(tenantId.getValue(), engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));

        AuditFinding finding = AuditFinding.create(tenantId.getValue(), engagementId, req.sourceExceptionId(),
                req.title(), req.description(), req.rootCause(), req.recommendation(), req.severity(),
                req.owner(), req.dueDate(), createdBy);
        findingRepo.save(finding);

        if (req.sourceExceptionId() != null) {
            AuditException ex = auditExceptionRepo.findByTenantAndId(tenantId.getValue(), req.sourceExceptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("AuditException", req.sourceExceptionId().toString()));
            try {
                ex.promote(finding.getId());
            } catch (IllegalStateException e) {
                throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
            }
            auditExceptionRepo.save(ex);
        }
        return toFindingResponse(finding);
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> getFindings(TenantId tenantId, UUID engagementId) {
        return findingRepo.findByEngagement(tenantId.getValue(), engagementId).stream()
                .map(this::toFindingResponse).toList();
    }

    @Transactional
    public FindingResponse recordManagementResponse(TenantId tenantId, UUID findingId, RecordManagementResponseRequest req) {
        AuditFinding f = findingRepo.findByTenantAndId(tenantId.getValue(), findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditFinding", findingId.toString()));
        f.recordManagementResponse(req.response());
        findingRepo.save(f);
        return toFindingResponse(f);
    }

    @Transactional
    public FindingResponse resolveFinding(TenantId tenantId, UUID findingId) {
        AuditFinding f = findingRepo.findByTenantAndId(tenantId.getValue(), findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditFinding", findingId.toString()));
        f.resolve();
        findingRepo.save(f);
        return toFindingResponse(f);
    }

    @Transactional
    public FindingResponse closeFinding(TenantId tenantId, UUID findingId) {
        AuditFinding f = findingRepo.findByTenantAndId(tenantId.getValue(), findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditFinding", findingId.toString()));
        try {
            f.close();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        findingRepo.save(f);
        return toFindingResponse(f);
    }

    @Transactional
    public FindingResponse reopenFinding(TenantId tenantId, UUID findingId, ReopenFindingRequest req) {
        AuditFinding f = findingRepo.findByTenantAndId(tenantId.getValue(), findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditFinding", findingId.toString()));
        f.reopen(req.reason());
        findingRepo.save(f);
        return toFindingResponse(f);
    }

    // FIX: closes the confirmed "no internal-audit engine reachable by
    // an external auditor" gap. Mirrors submitReportForApproval()'s own
    // "find whoever holds Head of Internal Audit on THIS engagement"
    // pattern just below — same authority source, same per-engagement
    // (not tenant-wide) scoping.
    @Transactional
    public FindingResponse shareFindingExternally(TenantId tenantId, UUID findingId,
                                                  ShareFindingRequest req, UUID actingUserId) {
        AuditFinding f = findingRepo.findByTenantAndId(tenantId.getValue(), findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditFinding", findingId.toString()));
        requireHeadOfInternalAudit(tenantId, f.getEngagementId(), actingUserId);
        try {
            f.share(actingUserId, req.reason());
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        findingRepo.save(f);
        return toFindingResponse(f);
    }

    @Transactional
    public FindingResponse withdrawExternalSharing(TenantId tenantId, UUID findingId, UUID actingUserId) {
        AuditFinding f = findingRepo.findByTenantAndId(tenantId.getValue(), findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditFinding", findingId.toString()));
        requireHeadOfInternalAudit(tenantId, f.getEngagementId(), actingUserId);
        try {
            f.withdraw();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        findingRepo.save(f);
        return toFindingResponse(f);
    }

    private void requireHeadOfInternalAudit(TenantId tenantId, UUID engagementId, UUID actingUserId) {
        boolean isHead = assignmentRepo.findByEngagement(tenantId.getValue(), engagementId).stream()
                .anyMatch(a -> "HEAD_OF_INTERNAL_AUDIT".equals(a.getRole()) && a.getUserId().equals(actingUserId));
        if (!isHead) {
            throw new HandyFlowException(
                    "Only the Head of Internal Audit on this engagement can share findings externally",
                    HttpStatus.FORBIDDEN, "NOT_HEAD_OF_INTERNAL_AUDIT");
        }
    }

    /**
     * Reuses ApprovalFacade.submitAdHoc() rather than the rule-matched
     * submit() — the report's approver is a real, specific person (the
     * engagement's own Head of Internal Audit, per EngagementAssignment
     * — the agreed engagement-scoped role model from Phase 1), not
     * something a tenant-wide condition-matched rule should decide.
     * This is exactly the case ChainEntryInput's own Javadoc already
     * anticipated: "a staff member picks the exact approver list...
     * fresh for each" submission, not AP's tenant-wide rule shape.
     * Fails clearly if no one holds that role on this engagement yet —
     * there is genuinely no one to approve it.
     */
    @Transactional
    public ApprovalRequestResponse submitReportForApproval(TenantId tenantId, UUID engagementId, UUID submittedBy) {
        engagementRepo.findByTenantAndId(tenantId.getValue(), engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));

        UUID headOfInternalAudit = assignmentRepo.findByEngagement(tenantId.getValue(), engagementId).stream()
                .filter(a -> "HEAD_OF_INTERNAL_AUDIT".equals(a.getRole()))
                .map(EngagementAssignment::getUserId)
                .findFirst()
                .orElseThrow(() -> new HandyFlowException(
                        "No one holds the Head of Internal Audit role on this engagement yet — assign one before submitting the report for sign-off",
                        HttpStatus.CONFLICT, "NO_APPROVER"));

        return approvalFacade.submitAdHoc(tenantId, "internalaudit", "ENGAGEMENT_REPORT", engagementId, submittedBy,
                ApprovalRule.ApprovalMode.SEQUENTIAL,
                List.of(new ChainEntryInput("USER", headOfInternalAudit.toString(), null, false)),
                Map.of());
    }

    @Transactional(readOnly = true)
    public ReportSignOffStatusResponse getReportApprovalStatus(TenantId tenantId, UUID engagementId) {
        return approvalFacade.getLatestRequestForEntity(tenantId, "internalaudit", "ENGAGEMENT_REPORT", engagementId)
                .map(r -> new ReportSignOffStatusResponse(r.status(), r.approvalMode()))
                .orElse(null);
    }

    private FindingResponse toFindingResponse(AuditFinding f) {
        String ownerName = f.getOwner() != null
                ? userRepo.findById(f.getOwner()).map(u -> (u.getFirstName() + " " + u.getLastName()).trim()).orElse("Unknown")
                : null;
        return new FindingResponse(f.getId(), f.getEngagementId(), f.getSourceExceptionId(), f.getTitle(),
                f.getDescription(), f.getRootCause(), f.getRecommendation(), f.getManagementResponse(),
                f.getSeverity(), f.getOwner(), ownerName, f.getDueDate(), f.getStatus(),
                f.getCreatedBy(), f.getCreatedAt(), f.getResolvedAt(),
                f.getClosedAt(), f.getPreviouslyClosedAt(), f.getReopenedAt(), f.getReopenReason(),
                f.getExternalVisibility(), f.getSharedAt(), f.getSharedBy(), f.getSharingReason());
    }
}
