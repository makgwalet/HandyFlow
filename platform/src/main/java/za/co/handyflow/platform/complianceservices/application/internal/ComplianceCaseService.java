package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.CaseDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Problems found for a client and the work to put them right. Every change leaves an event. A case needs an owner
 * before work starts, cannot be resolved with steps still open or without a note, and is closed by a second person
 * unless the same person records a reason.
 */
@Service
@RequiredArgsConstructor
public class ComplianceCaseService {

    private final ComplianceClientRepository clients;
    private final ComplianceAssessmentRepository assessments;
    private final ComplianceAssessmentItemRepository assessmentItems;
    private final ComplianceCaseRepository cases;
    private final ComplianceCaseStepRepository steps;
    private final ComplianceCaseEventRepository events;

    @Transactional(readOnly = true)
    public List<CaseSummary> list(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        List<ComplianceCase> found = cases.findForClient(tenantId, clientId);
        Map<UUID, List<ComplianceCaseStep>> byCase = stepsFor(tenantId, found);
        return found.stream().map(c -> summary(c, byCase.getOrDefault(c.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public CaseDetail get(TenantId tenantId, UUID clientId, UUID id) {
        return detail(tenantId, find(tenantId, clientId, id));
    }

    @Transactional
    public CaseDetail open(TenantId tenantId, UUID clientId, OpenRequest r, UUID by, LocalDate today) {
        requireClient(tenantId, clientId);
        String title = r.title();
        String description = r.description();
        String severity = r.severity();
        UUID obligationId = r.obligationId();
        String code = null;
        if (r.assessmentId() != null) {
            if (obligationId == null) throw new HandyFlowException("Say which obligation in the assessment this case is for", HttpStatus.BAD_REQUEST, "CASE_OBLIGATION_REQUIRED");
            assessments.findOne(tenantId, clientId, r.assessmentId()).orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
            ComplianceAssessmentItem item = assessmentItems.findOne(tenantId, r.assessmentId(), obligationId)
                    .orElseThrow(() -> new ResourceNotFoundException("That obligation is not part of the assessment"));
            if (!CaseRules.PROBLEM_STATUSES.contains(item.getStatus())) {
                throw new HandyFlowException("That obligation is not a problem in this assessment, so there is nothing to put right", HttpStatus.CONFLICT, "CASE_NOT_A_PROBLEM");
            }
            code = item.getCode();
            if (blank(title)) title = item.getCode() + ": " + item.getName();
            if (blank(severity)) severity = CaseRules.severityFromRisk(item.getRiskLevel());
        } else if (obligationId != null) {
            throw new HandyFlowException("A case for an obligation must come from an assessment", HttpStatus.BAD_REQUEST, "CASE_ASSESSMENT_REQUIRED");
        }
        if (obligationId != null && cases.findLive(tenantId, clientId, obligationId).isPresent()) {
            throw new HandyFlowException("There is already a case open for this obligation. Work on that one", HttpStatus.CONFLICT, "CASE_ALREADY_OPEN");
        }
        UUID owner = r.ownerId() != null ? r.ownerId() : by;
        ComplianceCase c = guard(() -> ComplianceCase.open(tenantId, clientId, title, description, severity, owner, r.dueDate(),
                r.assessmentId(), obligationId, code, by, today));
        cases.save(c);
        log(tenantId, c, "OPENED", null, c.getStatus(), null, by);
        return detail(tenantId, c);
    }

    @Transactional
    public CaseDetail transition(TenantId tenantId, UUID clientId, UUID id, TransitionRequest r, UUID by) {
        ComplianceCase c = find(tenantId, clientId, id);
        String from = c.getStatus();
        String action = r.action() == null ? "" : r.action().trim().toUpperCase();
        guard(() -> {
            switch (action) {
                case "START" -> c.start();
                case "AWAIT_CLIENT" -> c.awaitClient();
                case "RESUME" -> c.resume();
                case "RESOLVE" -> c.resolve(by, r.note(), openSteps(tenantId, c));
                case "CLOSE" -> c.close(by, r.note());
                case "REOPEN" -> c.reopen(r.note());
                case "CANCEL" -> c.cancel(r.note());
                default -> throw new IllegalArgumentException("Unknown action: " + r.action());
            }
            return null;
        });
        cases.save(c);
        log(tenantId, c, action, from, c.getStatus(), r.note(), by);
        return detail(tenantId, c);
    }

    @Transactional
    public CaseDetail assign(TenantId tenantId, UUID clientId, UUID id, OwnerRequest r, UUID by) {
        ComplianceCase c = find(tenantId, clientId, id);
        guard(() -> { c.assign(r.ownerId()); return null; });
        cases.save(c);
        log(tenantId, c, "OWNER_CHANGED", c.getStatus(), c.getStatus(), r.ownerId().toString(), by);
        return detail(tenantId, c);
    }

    @Transactional
    public CaseDetail reschedule(TenantId tenantId, UUID clientId, UUID id, DueRequest r, UUID by, LocalDate today) {
        ComplianceCase c = find(tenantId, clientId, id);
        guard(() -> { c.reschedule(r.dueDate(), today); return null; });
        cases.save(c);
        log(tenantId, c, "DUE_CHANGED", c.getStatus(), c.getStatus(), r.dueDate() == null ? "cleared" : r.dueDate().toString(), by);
        return detail(tenantId, c);
    }

    @Transactional
    public CaseDetail addStep(TenantId tenantId, UUID clientId, UUID id, StepRequest r, UUID by, LocalDate today) {
        ComplianceCase c = find(tenantId, clientId, id);
        requireLive(c);
        int next = stepsFor(tenantId, List.of(c)).getOrDefault(c.getId(), List.of()).stream().mapToInt(ComplianceCaseStep::getSortOrder).max().orElse(-1) + 1;
        ComplianceCaseStep s = guard(() -> ComplianceCaseStep.of(tenantId, c.getId(), r.description(), r.dueDate(), next, today));
        steps.save(s);
        log(tenantId, c, "STEP_ADDED", c.getStatus(), c.getStatus(), s.getDescription(), by);
        return detail(tenantId, c);
    }

    @Transactional
    public CaseDetail completeStep(TenantId tenantId, UUID clientId, UUID id, UUID stepId, UUID by) {
        ComplianceCase c = find(tenantId, clientId, id);
        requireLive(c);
        ComplianceCaseStep s = findStep(tenantId, c, stepId);
        guard(() -> { s.complete(by); return null; });
        steps.save(s);
        log(tenantId, c, "STEP_DONE", c.getStatus(), c.getStatus(), s.getDescription(), by);
        return detail(tenantId, c);
    }

    @Transactional
    public CaseDetail cancelStep(TenantId tenantId, UUID clientId, UUID id, UUID stepId, StepCancelRequest r, UUID by) {
        ComplianceCase c = find(tenantId, clientId, id);
        requireLive(c);
        ComplianceCaseStep s = findStep(tenantId, c, stepId);
        guard(() -> { s.cancel(r.reason()); return null; });
        steps.save(s);
        log(tenantId, c, "STEP_CANCELLED", c.getStatus(), c.getStatus(), s.getCancelReason(), by);
        return detail(tenantId, c);
    }

    // ---- helpers ----

    private int openSteps(TenantId tenantId, ComplianceCase c) {
        return (int) stepsFor(tenantId, List.of(c)).getOrDefault(c.getId(), List.of()).stream().filter(ComplianceCaseStep::isOpen).count();
    }

    private Map<UUID, List<ComplianceCaseStep>> stepsFor(TenantId tenantId, List<ComplianceCase> found) {
        if (found.isEmpty()) return Map.of();
        return steps.findForCases(tenantId, found.stream().map(ComplianceCase::getId).toList()).stream()
                .collect(Collectors.groupingBy(ComplianceCaseStep::getCaseId));
    }

    private CaseSummary summary(ComplianceCase c, List<ComplianceCaseStep> its) {
        int open = (int) its.stream().filter(ComplianceCaseStep::isOpen).count();
        return new CaseSummary(c.getId(), c.getTitle(), c.getSeverity(), c.getStatus(), c.getOwnerId(), c.getDueDate(), c.getAssessmentId(),
                c.getObligationId(), c.getObligationCode(), open, its.size(), c.getCreatedAt());
    }

    private CaseDetail detail(TenantId tenantId, ComplianceCase c) {
        List<ComplianceCaseStep> its = stepsFor(tenantId, List.of(c)).getOrDefault(c.getId(), List.of());
        List<StepView> stepViews = its.stream().map(s -> new StepView(s.getId(), s.getDescription(), s.getStatus(), s.getDueDate(), s.getDoneBy(), s.getDoneAt(), s.getCancelReason())).toList();
        List<EventView> eventViews = events.findForCase(tenantId, c.getId()).stream()
                .map(e -> new EventView(e.getEvent(), e.getFromStatus(), e.getToStatus(), e.getNote(), e.getActor(), e.getOccurredAt())).toList();
        return new CaseDetail(summary(c, its), c.getDescription(), c.getResolutionNote(), c.getResolvedBy(), c.getResolvedAt(), c.getClosedBy(),
                c.getClosedAt(), c.getCloseExceptionReason(), c.getCancelReason(), stepViews, eventViews);
    }

    private void log(TenantId tenantId, ComplianceCase c, String event, String from, String to, String note, UUID actor) {
        events.save(ComplianceCaseEvent.of(tenantId, c.getId(), event, from, to, note, actor));
    }

    private ComplianceClient requireClient(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private ComplianceCase find(TenantId tenantId, UUID clientId, UUID id) {
        requireClient(tenantId, clientId);
        ComplianceCase c = cases.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (!c.getClientId().equals(clientId)) throw new ResourceNotFoundException("Case not found");
        return c;
    }

    private ComplianceCaseStep findStep(TenantId tenantId, ComplianceCase c, UUID stepId) {
        return steps.findOne(tenantId, c.getId(), stepId).orElseThrow(() -> new ResourceNotFoundException("Step not found"));
    }

    private static void requireLive(ComplianceCase c) {
        if (!c.isLive()) throw new HandyFlowException("A case that is " + c.getStatus().toLowerCase().replace('_', ' ') + " can no longer change its steps", HttpStatus.CONFLICT, "CASE_NOT_LIVE");
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "CASE_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "CASE_INVALID");
        }
    }
}
