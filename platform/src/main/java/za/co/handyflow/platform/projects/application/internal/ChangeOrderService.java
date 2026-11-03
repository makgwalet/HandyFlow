package za.co.handyflow.platform.projects.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.projects.application.internal.change.ChangeEffectEngine;
import za.co.handyflow.platform.projects.application.internal.change.ChangeEffectEngine.Allocation;
import za.co.handyflow.platform.projects.application.internal.change.ChangeEffectEngine.LineState;
import za.co.handyflow.platform.projects.application.internal.change.ChangeEffectEngine.Problem;
import za.co.handyflow.platform.projects.application.internal.cost.CostEngine;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Forecast;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Node;
import za.co.handyflow.platform.projects.application.internal.schedule.SimpleWorkingCalendar;
import za.co.handyflow.platform.projects.domain.model.*;
import za.co.handyflow.platform.projects.domain.repository.*;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Change control: raise, assess, approve and apply changes to scope, cost and schedule.
 *
 * LIFECYCLE (see ChangeOrderStatus): DRAFT → SUBMITTED → APPROVED | REJECTED | WITHDRAWN;
 * REJECTED → DRAFT as a new revision. Every submission is kept (change_order_revisions).
 *
 * APPROVAL runs through the shared approval engine exactly as before (module "projects", entity
 * type CHANGE_ORDER, one PM_APPROVE step), and then — in the SAME transaction — applies the change
 * to the project through ChangeApplicationService. If applying fails, the approval rolls back.
 * Before the engine is touched, the change is re-validated against the CURRENT budget, because
 * spending may have happened since it was submitted.
 *
 * NEW IN THIS CHUNK
 *  - structured impact: cost allocations per budget line, affected tasks with working-day delays,
 *    and scope / risk / procurement notes;
 *  - edit while DRAFT, withdraw, reject-then-reopen-and-resubmit, client sign-off and disputes;
 *  - originator cannot approve their own change order when
 *    app.projects.change-orders.allow-self-approval=false (default true, as before);
 *  - impact analysis (read-only): budget before/after and the forecast finish with and without the delays;
 *  - supporting evidence attachments (same EvidenceFacade pattern as RFIs).
 *
 * KNOWN LIMITATIONS
 *  - Withdrawing a SUBMITTED change order does NOT close its open request in the approval engine
 *    (no cancel call was visible, and a failed call inside this transaction would poison it). The
 *    stale request stays pending in the approvals module but cannot affect the project: effects are
 *    applied only here, and a WITHDRAWN change order can no longer be approved.
 *  - Approval thresholds (different approvers by value) depend on rules configured in the approvals
 *    module; this class passes no metadata to them. Add it once the rule condition keys are known.
 *  - Links to Contracting / Invoicing are not modelled (those modules' APIs weren't visible).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeOrderService {

    private static final String APPROVALS_MODULE      = "projects";
    private static final String APPROVALS_ENTITY_TYPE = "CHANGE_ORDER";

    private final ChangeOrderRepository         changeOrderRepo;
    private final ProjectRepository             projectRepo;
    private final ProjectBudgetLineRepository   budgetRepo;
    private final ProjectTaskRepository         taskRepo;
    private final TaskDependencyRepository      depRepo;
    private final SequenceService               sequenceService;
    private final PmNotificationService         notificationService;
    private final ApprovalFacade                approvalFacade;
    private final ChangeOrderCostLineRepository costLineRepo;
    private final ChangeOrderTaskRepository     coTaskRepo;
    private final ChangeOrderRevisionRepository revisionRepo;
    private final ChangeApplicationService      applicationService;
    private final ProjectReferenceValidator     referenceValidator;
    private final ProjectCalendarProvider       calendars;
    private final ProjectCostService            costService;
    private final EvidenceFacade                evidenceFacade;
    private final ObjectMapper                  objectMapper;

    @PersistenceContext
    private EntityManager em;

    @Value("${app.projects.change-orders.allow-self-approval:true}")
    private boolean allowSelfApproval;

    @Value("${app.projects.attachments.max-bytes:20971520}")
    private long maxAttachmentBytes;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChangeOrder> getChangeOrders(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return changeOrderRepo.findByProject(projectId);
    }

    @Transactional(readOnly = true)
    public ChangeOrderDetailResponse getDetail(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        Map<UUID, ProjectTask> tasks = taskRepo.findByProject(co.getProjectId()).stream()
                .collect(Collectors.toMap(ProjectTask::getId, t -> t));

        List<ChangeOrderDetailResponse.CostLineView> lines = costLineRepo.findByChangeOrder(id).stream()
                .map(l -> new ChangeOrderDetailResponse.CostLineView(
                        l.getId(), l.getBudgetLineId(), l.getCategory(), l.getDescription(), l.getAmount()))
                .toList();
        List<ChangeOrderDetailResponse.AffectedTaskView> affected = coTaskRepo.findByChangeOrder(id).stream()
                .map(l -> {
                    ProjectTask t = tasks.get(l.getTaskId());
                    return new ChangeOrderDetailResponse.AffectedTaskView(l.getTaskId(),
                            t != null ? t.getTaskNumber() : null, t != null ? t.getTitle() : null, l.getDelayDays());
                }).toList();
        List<ChangeOrderDetailResponse.RevisionView> revisions = revisionRepo.findByChangeOrder(id).stream()
                .map(r -> new ChangeOrderDetailResponse.RevisionView(r.getRevision(), r.getTitle(),
                        r.getCostImpact(), r.getScheduleImpact(), r.getSubmittedByName(), r.getSubmittedAt(),
                        r.getOutcome(), r.getOutcomeReason(), r.getDecidedAt()))
                .toList();
        List<UUID> rfis = em.createQuery("SELECT r.id FROM ProjectRfi r WHERE r.changeOrderId = :id", UUID.class)
                .setParameter("id", id).getResultList();

        return new ChangeOrderDetailResponse(ChangeOrderResponse.of(co), lines, affected, revisions, rfis);
    }

    // ── Create / edit (DRAFT only) ────────────────────────────────────────────

    @Transactional
    public ChangeOrder createChangeOrder(TenantId tenantId, UUID projectId,
                                         CreateChangeOrderRequest req, UUID createdBy) {
        Project project = verifyProject(tenantId, projectId);
        requireEditable(project);
        BigDecimal impact = effectiveCostImpact(req);
        validateShape(tenantId, projectId, req);

        String number = sequenceService.nextChangeOrderNumber(tenantId.getValue(), projectId);
        ChangeOrder co = ChangeOrder.create(tenantId.getValue(), projectId, number,
                req.title(), req.description(), req.reason(), impact, req.scheduleImpact(), createdBy);
        co.updateDetails(req.title(), req.description(), req.reason(), impact, req.scheduleImpact(),
                req.scopeImpact(), req.riskImpact(), req.procurementImpact());
        ChangeOrder saved = changeOrderRepo.save(co);
        replaceImpact(tenantId, saved, req);
        log.info("Created change order={} number='{}' project={}", saved.getId(), number, projectId);
        return saved;
    }

    @Transactional
    public ChangeOrder updateChangeOrder(TenantId tenantId, UUID id, CreateChangeOrderRequest req) {
        ChangeOrder co = find(tenantId, id);
        Project project = verifyProject(tenantId, co.getProjectId());
        requireEditable(project);
        BigDecimal impact = effectiveCostImpact(req);
        validateShape(tenantId, co.getProjectId(), req);

        co.updateDetails(req.title(), req.description(), req.reason(), impact, req.scheduleImpact(),
                req.scopeImpact(), req.riskImpact(), req.procurementImpact());   // throws unless DRAFT
        ChangeOrder saved = changeOrderRepo.save(co);
        replaceImpact(tenantId, saved, req);
        return saved;
    }

    // ── Submit / approve / reject / withdraw / reopen ─────────────────────────

    @Transactional
    public ChangeOrder submitChangeOrder(TenantId tenantId, UUID id, UUID userId, String userName) {
        ChangeOrder co = findForUpdate(tenantId, id);
        Project project = verifyProject(tenantId, co.getProjectId());
        requireEditable(project);
        throwIfProblems(validateForApplication(tenantId, co), "CHANGE_NOT_SUBMITTABLE", HttpStatus.UNPROCESSABLE_ENTITY);

        co.submit();
        changeOrderRepo.save(co);
        revisionRepo.save(ChangeOrderRevision.open(co, snapshotJson(co), userId, userName));
        approvalFacade.submit(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id, null, Map.of());
        log.info("Submitted change order={} revision={} project={}", id, co.getRevision(), co.getProjectId());
        return co;
    }

    @Transactional
    public ChangeOrder approveChangeOrder(TenantId tenantId, UUID id, UUID approverId, String approverName) {
        ChangeOrder co = findForUpdate(tenantId, id);
        // Approval changes the budget and schedule, so it is refused on a closed or cancelled project.
        // Lock order is always: change order row, cost advisory lock, project row. Cost postings take the
        // advisory lock and then update the project row, so taking the row first would deadlock with them.
        costService.lockProject(co.getProjectId());
        requireEditable(projectRepo.findByTenantAndIdForUpdate(tenantId.getValue(), co.getProjectId())
                .orElseThrow(() -> notFound("Project")));
        if (!"SUBMITTED".equals(co.getStatus())) {
            throw new HandyFlowException("Only a submitted change order can be approved (this one is "
                    + co.getStatus() + ")", HttpStatus.CONFLICT, "INVALID_STATE");
        }

        if (!allowSelfApproval && approverId != null && approverId.equals(co.getSubmittedBy())) {
            throw new HandyFlowException("You cannot approve a change order you raised",
                    HttpStatus.FORBIDDEN, "SELF_APPROVAL_NOT_ALLOWED");
        }
        // The budget may have moved since submission — check BEFORE touching the approval engine.
        throwIfProblems(validateForApplication(tenantId, co), "CHANGE_NOT_APPLICABLE", HttpStatus.CONFLICT);

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id);
        ApprovalRequestResponse result;
        if (existing.isPresent() && isOpen(existing.get())) {
            ApprovalStepResponse pendingStep = firstPendingStep(existing.get())
                    .orElseThrow(() -> new HandyFlowException(
                            "This change order's approval request has no pending step — data inconsistency, needs manual review",
                            HttpStatus.CONFLICT, "NO_PENDING_STEP"));
            result = approvalFacade.actOnStep(tenantId, pendingStep.id(), approverId,
                    currentUserAuthorities(), "APPROVE", null, null);
        } else {
            result = approvalFacade.submit(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id, approverId, Map.of());
            if (isOpen(result)) {
                ApprovalStepResponse firstStep = firstPendingStep(result)
                        .orElseThrow(() -> new IllegalStateException("A freshly-submitted request has no pending step"));
                result = approvalFacade.actOnStep(tenantId, firstStep.id(), approverId,
                        currentUserAuthorities(), "APPROVE", null, null);
            }
        }
        if (!"APPROVED".equals(result.status())) {
            throw new HandyFlowException("Approval did not complete — status: " + result.status(),
                    HttpStatus.BAD_REQUEST, "APPROVAL_NOT_COMPLETE");
        }

        co.approve(approverId, approverName);                   // status check lives in ChangeOrderStatus

        Project project = projectRepo.findByTenantAndId(tenantId.getValue(), co.getProjectId())
                .orElseThrow(() -> notFound("Project"));
        applicationService.apply(tenantId, co, project, approverId, approverName);

        ChangeOrder saved = changeOrderRepo.save(co);
        recordOutcome(saved, "APPROVED", null, approverId);
        notificationService.notifyChangeOrderApproved(
                co.getTenantId(), project.getName(), co.getChangeNumber(), approverName);
        return saved;
    }

    @Transactional
    public ChangeOrder rejectChangeOrder(TenantId tenantId, UUID id, String reason) {
        String why = requireReason(reason);
        ChangeOrder co = findForUpdate(tenantId, id);

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id);
        if (existing.isPresent() && isOpen(existing.get())) {
            ApprovalStepResponse pendingStep = firstPendingStep(existing.get()).orElse(null);
            if (pendingStep != null) {
                approvalFacade.actOnStep(tenantId, pendingStep.id(), currentUserId(),
                        currentUserAuthorities(), "REJECT", why, null);
            }
        }
        co.reject(why);
        ChangeOrder saved = changeOrderRepo.save(co);
        recordOutcome(saved, "REJECTED", why, currentUserId());
        log.info("Rejected change order={} project={}", id, co.getProjectId());
        return saved;
    }

    @Transactional
    public ChangeOrder withdraw(TenantId tenantId, UUID id, String reason) {
        String why = requireReason(reason);
        ChangeOrder co = findForUpdate(tenantId, id);
        co.withdraw(why);
        ChangeOrder saved = changeOrderRepo.save(co);
        recordOutcome(saved, "WITHDRAWN", why, currentUserId());   // no-op for a DRAFT that was never submitted
        log.info("Withdrew change order={} project={}", id, co.getProjectId());
        return saved;
    }

    /** REJECTED → DRAFT as a new revision, ready to edit and resubmit. */
    @Transactional
    public ChangeOrder reopen(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        requireEditable(verifyProject(tenantId, co.getProjectId()));
        co.reopenForRevision();
        log.info("Reopened change order={} as revision {}", id, co.getRevision());
        return changeOrderRepo.save(co);
    }

    // ── Client side ───────────────────────────────────────────────────────────

    @Transactional
    public ChangeOrder markClientApproved(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        co.markClientApproved();
        return changeOrderRepo.save(co);
    }

    @Transactional
    public ChangeOrder raiseClientDispute(TenantId tenantId, UUID id, String reason) {
        ChangeOrder co = find(tenantId, id);
        co.raiseClientDispute(requireReason(reason));
        return changeOrderRepo.save(co);
    }

    @Transactional
    public ChangeOrder resolveClientDispute(TenantId tenantId, UUID id, String resolution) {
        ChangeOrder co = find(tenantId, id);
        co.resolveClientDispute(requireReason(resolution));
        return changeOrderRepo.save(co);
    }

    // ── Impact analysis (read-only) ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public ImpactAnalysisResponse impactAnalysis(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        UUID projectId = co.getProjectId();
        List<String> warnings = new ArrayList<>();

        // Cost
        List<Allocation> allocations = allocationsOf(co);
        List<Problem> problems = validateForApplication(tenantId, co);
        CostEngine.Summary summary = costService.summary(tenantId, projectId);
        BigDecimal after = ChangeEffectEngine.revisedBudgetAfter(summary.revisedBudget(), co.getCostImpact(), allocations);
        boolean exceeds = co.getCostImpact().signum() > 0
                && co.getCostImpact().compareTo(summary.contingencyRemaining()) > 0;
        if (exceeds) warnings.add("The cost increase (" + co.getCostImpact() + ") is more than the contingency remaining ("
                + summary.contingencyRemaining() + ")");
        problems.forEach(p -> warnings.add(p.message()));

        // Schedule: forecast with and without the affected tasks' delays
        List<ProjectTask> tasks = taskRepo.findByProject(projectId);
        List<TaskDependency> deps = depRepo.findByProject(tenantId.getValue(), projectId);
        LocalDate today = LocalDate.now(ScheduleService.SAST);
        SimpleWorkingCalendar cal = calendarFor(tasks, today);

        List<ChangeOrderTask> links = coTaskRepo.findByChangeOrder(id);
        Map<UUID, Integer> delays = links.stream().collect(Collectors.toMap(ChangeOrderTask::getTaskId, ChangeOrderTask::getDelayDays));

        List<Node> baseNodes = ScheduleService.toNodes(tasks);
        List<Node> modNodes = baseNodes.stream().map(n -> {
            int d = delays.getOrDefault(n.id(), 0);
            if (d <= 0 || n.plannedEnd() == null || "COMPLETED".equals(n.status()) || "CANCELLED".equals(n.status())) return n;
            return new Node(n.id(), n.plannedStart(), cal.plusWorkingDays(cal.rollBack(n.plannedEnd()), d),
                    n.actualStart(), n.actualEnd(), n.status(), n.progressPct());
        }).toList();

        Forecast before = ScheduleEngine.forecast(baseNodes, ScheduleService.toDeps(deps), cal, today);
        Forecast afterF = ScheduleEngine.forecast(modNodes, ScheduleService.toDeps(deps), cal, today);

        Integer delta = (before.finish() != null && afterF.finish() != null)
                ? (int) ChronoUnit.DAYS.between(before.finish(), afterF.finish()) : null;
        Set<UUID> criticalBefore = before.results().values().stream()
                .filter(ScheduleEngine.NodeResult::critical).map(ScheduleEngine.NodeResult::id).collect(Collectors.toSet());
        List<UUID> newlyCritical = afterF.results().values().stream()
                .filter(ScheduleEngine.NodeResult::critical).map(ScheduleEngine.NodeResult::id)
                .filter(t -> !criticalBefore.contains(t)).toList();

        if (before.cyclic()) warnings.add("The task dependencies contain a cycle — the schedule effect cannot be computed");
        if (delta != null && !links.isEmpty() && delta != co.getScheduleImpact()) {
            warnings.add("Declared schedule impact is " + co.getScheduleImpact() + " day(s) but the affected task delays move the forecast finish by "
                    + delta + " calendar day(s)");
        }
        for (ChangeOrderTask l : links) {
            ProjectTask t = tasks.stream().filter(x -> x.getId().equals(l.getTaskId())).findFirst().orElse(null);
            if (t != null && "COMPLETED".equals(t.getStatus()) && l.getDelayDays() > 0) {
                warnings.add("Task " + ScheduleService.label(t) + " is already completed — its delay will be ignored");
            }
        }

        return new ImpactAnalysisResponse(id, co.getChangeNumber(),
                new ImpactAnalysisResponse.Cost(co.getCostImpact(), summary.revisedBudget(), after,
                        summary.contingencyRemaining(), exceeds, problems.stream().map(Problem::message).toList()),
                new ImpactAnalysisResponse.Schedule(co.getScheduleImpact(), before.finish(), afterF.finish(), delta,
                        links.size(), newlyCritical, before.cyclic()),
                warnings);
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @Transactional
    public EvidenceResponse attachEvidence(TenantId tenantId, UUID id, MultipartFile file,
                                           UUID uploadedBy, String uploadedByName) {
        if (file == null || file.isEmpty()) {
            throw new HandyFlowException("A non-empty file is required", HttpStatus.BAD_REQUEST, "FILE_REQUIRED");
        }
        if (file.getSize() > maxAttachmentBytes) {
            throw new HandyFlowException(
                    "File exceeds the maximum allowed size of " + (maxAttachmentBytes / (1024 * 1024)) + " MB",
                    HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        ChangeOrder co = find(tenantId, id);
        return evidenceFacade.attach(tenantId, file, "CHANGE_ORDER_ATTACHMENT", "projects", "ChangeOrder",
                co.getId(), null, uploadedBy, uploadedByName);
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getAttachments(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        return evidenceFacade.listFor(tenantId, "projects", "ChangeOrder", co.getId());
    }

    /** One attached file, only if it is attached to THIS change order. */
    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence downloadAttachment(TenantId tenantId, UUID id, UUID evidenceId) {
        ChangeOrder co = find(tenantId, id);
        boolean attached = evidenceFacade.listFor(tenantId, "projects", "ChangeOrder", co.getId()).stream()
                .anyMatch(e -> e.id().equals(evidenceId));
        if (!attached) throw notFound("Attachment");
        return evidenceFacade.download(tenantId, evidenceId);
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private List<Problem> validateForApplication(TenantId tenantId, ChangeOrder co) {
        Map<UUID, LineState> states = budgetRepo.findByProject(co.getProjectId()).stream()
                .collect(Collectors.toMap(ProjectBudgetLine::getId, l -> new LineState(
                        l.getId(), l.getBudgetedAmount(), l.getActualAmount(), l.getCommittedAmount())));
        return new ArrayList<>(ChangeEffectEngine.validateAllocations(co.getCostImpact(), allocationsOf(co), states));
    }

    private List<Allocation> allocationsOf(ChangeOrder co) {
        return costLineRepo.findByChangeOrder(co.getId()).stream()
                .map(l -> new Allocation(l.getBudgetLineId(), l.getCategory(), l.getAmount())).toList();
    }

    private static void throwIfProblems(List<Problem> problems, String code, HttpStatus status) {
        if (problems.isEmpty()) return;
        String message = problems.stream().map(Problem::message).collect(Collectors.joining("; "));
        throw new HandyFlowException(message, status, code);
    }

    private BigDecimal effectiveCostImpact(CreateChangeOrderRequest req) {
        if (req.costImpact() != null) return req.costImpact();
        if (req.costLines() == null || req.costLines().isEmpty()) return BigDecimal.ZERO;
        return req.costLines().stream().map(CreateChangeOrderRequest.CostLineRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Structural checks on the request itself; the money rules are ChangeEffectEngine's. */
    private void validateShape(TenantId tenantId, UUID projectId, CreateChangeOrderRequest req) {
        if (req.costLines() != null) {
            Set<UUID> projectLines = budgetRepo.findByProject(projectId).stream()
                    .map(ProjectBudgetLine::getId).collect(Collectors.toSet());
            for (CreateChangeOrderRequest.CostLineRequest l : req.costLines()) {
                if (l.amount().signum() == 0) {
                    throw new HandyFlowException("A cost line cannot be zero", HttpStatus.BAD_REQUEST, "ZERO_AMOUNT");
                }
                if (l.budgetLineId() != null && !projectLines.contains(l.budgetLineId())) {
                    throw new HandyFlowException("Budget line not found in this project",
                            HttpStatus.BAD_REQUEST, "REFERENCE_MISMATCH");
                }
            }
        }
        if (req.affectedTasks() != null) {
            Set<UUID> seen = new HashSet<>();
            for (CreateChangeOrderRequest.AffectedTaskRequest t : req.affectedTasks()) {
                if (!seen.add(t.taskId())) {
                    throw new HandyFlowException("A task can only be listed once", HttpStatus.BAD_REQUEST, "DUPLICATE_TASK");
                }
                referenceValidator.requireTaskInProject(tenantId, projectId, t.taskId());
            }
        }
    }

    private void replaceImpact(TenantId tenantId, ChangeOrder co, CreateChangeOrderRequest req) {
        costLineRepo.deleteByChangeOrder(co.getId());
        coTaskRepo.deleteByChangeOrder(co.getId());
        if (req.costLines() != null) {
            costLineRepo.saveAll(req.costLines().stream().map(l -> ChangeOrderCostLine.create(
                    tenantId.getValue(), co.getId(), l.budgetLineId(),
                    blankToNull(l.category()), blankToNull(l.description()), l.amount())).toList());
        }
        if (req.affectedTasks() != null) {
            coTaskRepo.saveAll(req.affectedTasks().stream().map(t -> ChangeOrderTask.create(
                    tenantId.getValue(), co.getId(), t.taskId(), t.delayDays())).toList());
        }
    }

    // ── History helpers ───────────────────────────────────────────────────────

    private String snapshotJson(ChangeOrder co) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("costLines", costLineRepo.findByChangeOrder(co.getId()).stream().map(l -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("budgetLineId", l.getBudgetLineId());
            x.put("category", l.getCategory());
            x.put("description", l.getDescription());
            x.put("amount", l.getAmount());
            return x;
        }).toList());
        m.put("affectedTasks", coTaskRepo.findByChangeOrder(co.getId()).stream().map(t -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("taskId", t.getTaskId());
            x.put("delayDays", t.getDelayDays());
            return x;
        }).toList());
        m.put("scopeImpact", co.getScopeImpact());
        m.put("riskImpact", co.getRiskImpact());
        m.put("procurementImpact", co.getProcurementImpact());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise change order snapshot", e);
        }
    }

    private void recordOutcome(ChangeOrder co, String outcome, String reason, UUID by) {
        revisionRepo.findByChangeOrderAndRevision(co.getId(), co.getRevision()).ifPresent(r -> {
            r.decide(outcome, reason, by);
            revisionRepo.save(r);
        });
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    /** Locked read for every state change (see ChangeOrderRepository.findByTenantAndIdForUpdate). */
    private ChangeOrder findForUpdate(TenantId tenantId, UUID id) {
        return changeOrderRepo.findByTenantAndIdForUpdate(tenantId.getValue(), id)
                .orElseThrow(() -> notFound("Change order"));
    }

    private ChangeOrder find(TenantId tenantId, UUID id) {
        return changeOrderRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> notFound("Change order"));
    }

    private Project verifyProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private static void requireEditable(Project p) {
        if (!p.getStatus().isEditable()) {
            throw new HandyFlowException("Project is " + p.getStatus() + " and can no longer be modified",
                    HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new HandyFlowException("A reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }
        return reason.trim();
    }

    private SimpleWorkingCalendar calendarFor(List<ProjectTask> tasks, LocalDate today) {
        LocalDate from = today, to = today;
        for (ProjectTask t : tasks) {
            if (t.getPlannedStart() != null && t.getPlannedStart().isBefore(from)) from = t.getPlannedStart();
            if (t.getActualStart()  != null && t.getActualStart().isBefore(from))  from = t.getActualStart();
            if (t.getPlannedEnd()   != null && t.getPlannedEnd().isAfter(to))      to = t.getPlannedEnd();
        }
        return calendars.forRange(from, to);
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private static HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private boolean isOpen(ApprovalRequestResponse r) {
        return "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status());
    }

    private Optional<ApprovalStepResponse> firstPendingStep(ApprovalRequestResponse r) {
        return r.steps().stream()
                .filter(s -> "PENDING".equals(s.status()))
                .min(Comparator.comparingInt(ApprovalStepResponse::stepOrder));
    }

    private List<String> currentUserAuthorities() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}