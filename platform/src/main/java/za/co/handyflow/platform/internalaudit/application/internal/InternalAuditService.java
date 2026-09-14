package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AnnualAuditPlan;
import za.co.handyflow.platform.internalaudit.domain.model.AuditEngagement;
import za.co.handyflow.platform.internalaudit.domain.model.AuditPlanEntry;
import za.co.handyflow.platform.internalaudit.domain.model.AuditUniverseEntry;
import za.co.handyflow.platform.internalaudit.domain.model.EngagementAssignment;
import za.co.handyflow.platform.internalaudit.domain.model.RiskAssessment;
import za.co.handyflow.platform.internalaudit.domain.repository.AnnualAuditPlanRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditEngagementRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditPlanEntryRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditUniverseEntryRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.EngagementAssignmentRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.RiskAssessmentRepository;
import za.co.handyflow.platform.internalaudit.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
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
        return new EngagementResponse(e.getId(), e.getPlanEntryId(), e.getUniverseEntryId(), universeEntryName,
                e.getName(), e.getStatus(), e.getStartDate(), e.getEndDate(), e.getCreatedBy(), e.getCreatedAt(),
                assignments);
    }

    private EngagementAssignmentResponse toAssignmentResponse(EngagementAssignment a) {
        String userName = userRepo.findById(a.getUserId())
                .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .orElse("Unknown");
        return new EngagementAssignmentResponse(a.getId(), a.getUserId(), userName, a.getRole(),
                a.getAssignedBy(), a.getAssignedAt());
    }
}
