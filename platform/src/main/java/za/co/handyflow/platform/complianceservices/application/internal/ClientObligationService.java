package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ClientObligationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Works out which catalogue obligations apply to a client, and keeps a person's decisions beside the suggestions. */
@Service
@RequiredArgsConstructor
public class ClientObligationService {

    private final ComplianceClientRepository clients;
    private final ComplianceClientFieldSourceRepository fieldSources;
    private final ComplianceObligationRepository obligations;
    private final ComplianceObligationVersionRepository versions;
    private final ComplianceObligationConditionRepository conditions;
    private final ComplianceClientObligationRepository assignments;
    private final ComplianceClientObligationHistoryRepository history;

    @Transactional(readOnly = true)
    public AssignmentsResponse list(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return build(tenantId, assignments.findForClient(tenantId, clientId));
    }

    /** Re-reads the client's profile and the catalogue, adds what is new and updates suggestions. People's decisions stay. */
    @Transactional
    public AssignmentsResponse refresh(TenantId tenantId, UUID clientId, UUID by, LocalDate today) {
        ComplianceClient client = requireClient(tenantId, clientId);
        ApplicabilityRules.Facts facts = factsOf(client);
        Set<String> confirmed = fieldSources.findForClient(tenantId, clientId).stream()
                .filter(ComplianceClientFieldSource::isVerified).map(ComplianceClientFieldSource::getFieldName).collect(Collectors.toSet());

        Map<UUID, List<ComplianceObligationVersion>> byObligation = versions.findAllForTenant(tenantId).stream()
                .collect(Collectors.groupingBy(ComplianceObligationVersion::getObligationId));
        Map<UUID, ComplianceObligationVersion> inForce = new LinkedHashMap<>();
        for (ComplianceObligation o : obligations.findAllForTenant(tenantId)) {
            List<ComplianceObligationVersion> vs = byObligation.getOrDefault(o.getId(), List.of());
            Integer n = ObligationRules.effectiveOn(vs.stream().map(ComplianceObligationVersion::span).toList(), today);
            if (n != null) vs.stream().filter(v -> v.getVersionNumber() == n).findFirst().ifPresent(v -> inForce.put(o.getId(), v));
        }
        Map<UUID, List<ObligationRules.Condition>> conds = new HashMap<>();
        if (!inForce.isEmpty()) {
            for (ComplianceObligationCondition c : conditions.findForVersions(tenantId, inForce.values().stream().map(ComplianceObligationVersion::getId).toList())) {
                conds.computeIfAbsent(c.getVersionId(), k -> new ArrayList<>()).add(new ObligationRules.Condition(c.getFieldName(), c.getOperator(), c.getValue()));
            }
        }
        Map<UUID, ComplianceClientObligation> existing = assignments.findForClient(tenantId, clientId).stream()
                .collect(Collectors.toMap(ComplianceClientObligation::getObligationId, a -> a));

        for (var entry : inForce.entrySet()) {
            ComplianceObligationVersion v = entry.getValue();
            ApplicabilityRules.Evaluation e = ApplicabilityRules.evaluate(conds.getOrDefault(v.getId(), List.of()), facts, confirmed);
            ComplianceClientObligation a = existing.get(entry.getKey());
            if (a == null) {
                a = assignments.save(ComplianceClientObligation.suggested(tenantId, clientId, entry.getKey(), v.getId(), v.getVersionNumber(), e));
                history.save(ComplianceClientObligationHistory.of(tenantId, a.getId(), null, a.getState(), "SUGGESTED", a.getSuggestionReasons(), by, v.getVersionNumber()));
            } else {
                String before = a.getState();
                if (a.resuggest(v.getId(), v.getVersionNumber(), e)) {
                    history.save(ComplianceClientObligationHistory.of(tenantId, a.getId(), before, a.getState(), "RESUGGESTED", a.getSuggestionReasons(), by, v.getVersionNumber()));
                }
                assignments.save(a);
            }
        }
        return build(tenantId, assignments.findForClient(tenantId, clientId));
    }

    @Transactional
    public AssignmentsResponse decide(TenantId tenantId, UUID clientId, UUID obligationId, DecisionRequest r, UUID by, LocalDate today) {
        requireClient(tenantId, clientId);
        ComplianceClientObligation a = find(tenantId, clientId, obligationId);
        String before = a.getState();
        try {
            a.decide(r.state(), r.reason(), r.evidenceRef(), r.effectiveFrom(), r.reviewDue(), by, today);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "CLIENT_OBLIGATION_INVALID");
        }
        assignments.save(a);
        history.save(ComplianceClientObligationHistory.of(tenantId, a.getId(), before, a.getState(), "DECIDED", a.getDecisionReason(), by, a.getVersionNumber()));
        return build(tenantId, assignments.findForClient(tenantId, clientId));
    }

    @Transactional
    public AssignmentsResponse approve(TenantId tenantId, UUID clientId, UUID obligationId, String exceptionReason, UUID by) {
        requireClient(tenantId, clientId);
        ComplianceClientObligation a = find(tenantId, clientId, obligationId);
        try {
            a.approve(by, exceptionReason);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "CLIENT_OBLIGATION_NOT_AWAITING");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "CLIENT_OBLIGATION_INVALID");
        }
        assignments.save(a);
        history.save(ComplianceClientObligationHistory.of(tenantId, a.getId(), a.getState(), a.getState(), "APPROVED", a.getApprovalExceptionReason(), by, a.getVersionNumber()));
        return build(tenantId, assignments.findForClient(tenantId, clientId));
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(TenantId tenantId, UUID clientId, UUID obligationId) {
        requireClient(tenantId, clientId);
        ComplianceClientObligation a = find(tenantId, clientId, obligationId);
        return history.findForAssignment(tenantId, a.getId()).stream().map(h -> new HistoryView(h.getFromState(), h.getToState(), h.getEvent(),
                h.getReason(), h.getActor(), h.getVersionNumber(), h.getOccurredAt())).toList();
    }

    // ---- helpers ----

    static ApplicabilityRules.Facts factsOf(ComplianceClient c) {
        return new ApplicabilityRules.Facts(c.getEntityType(), c.getIndustry(), c.getEmployeeCount(), c.getTurnoverBand(),
                c.getVatNumber() != null ? Boolean.TRUE : null);
    }

    private AssignmentsResponse build(TenantId tenantId, List<ComplianceClientObligation> list) {
        Map<UUID, ComplianceObligation> byId = obligations.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligation::getId, o -> o));
        Map<UUID, ComplianceObligationVersion> vById = versions.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligationVersion::getId, v -> v));
        LocalDate today = LocalDate.now();
        List<AssignmentView> views = list.stream().map(a -> {
            ComplianceObligation o = byId.get(a.getObligationId());
            ComplianceObligationVersion v = vById.get(a.getVersionId());
            boolean overridden = a.isDecidedByPerson() && !a.getState().equals(a.getSuggestedState());
            boolean review = a.isDecidedByPerson() && ApplicabilityRules.reviewNeeded(a.getDecidedVersionNumber(), a.getVersionNumber(), a.getReviewDue(), today);
            return new AssignmentView(a.getObligationId(), o == null ? null : o.getCode(), v == null ? null : v.getName(), a.getVersionNumber(),
                    a.getState(), a.effectiveState(), a.getSuggestedState(),
                    a.getSuggestionReasons() == null ? List.of() : Arrays.asList(a.getSuggestionReasons().split("\n")),
                    a.isBasedOnUnconfirmed(), overridden, a.getDecidedBy(), a.getDecidedAt(), a.getDecisionReason(), a.getEvidenceRef(),
                    a.getEffectiveFrom(), a.getReviewDue(), a.getApprovalStatus(), a.getApprovedBy(), review);
        }).sorted(Comparator.comparing(AssignmentView::code, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        Counts counts = new Counts(
                (int) views.stream().filter(v -> v.effectiveState().equals(ApplicabilityRules.APPLICABLE)).count(),
                (int) views.stream().filter(v -> v.effectiveState().equals(ApplicabilityRules.NOT_APPLICABLE)).count(),
                (int) views.stream().filter(v -> v.effectiveState().equals(ApplicabilityRules.PENDING)).count(),
                (int) views.stream().filter(v -> v.approvalStatus().equals("AWAITING")).count(),
                (int) views.stream().filter(AssignmentView::reviewNeeded).count());
        return new AssignmentsResponse(views, counts);
    }

    private ComplianceClient requireClient(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private ComplianceClientObligation find(TenantId tenantId, UUID clientId, UUID obligationId) {
        return assignments.findOne(tenantId, clientId, obligationId)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceClientObligation", obligationId.toString()));
    }
}
