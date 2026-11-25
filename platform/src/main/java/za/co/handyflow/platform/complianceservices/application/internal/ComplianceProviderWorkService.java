package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ProviderWorkDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Giving work to outside providers and following it through. Nothing starts until the guards in ProviderWorkRules are met. */
@Service
@RequiredArgsConstructor
public class ComplianceProviderWorkService {

    private final ComplianceProviderAssignmentRepository assignments;
    private final ComplianceProviderDeliverableRepository deliverables;
    private final ComplianceProviderRepository providers;
    private final ComplianceProviderAccreditationRepository accreditations;
    private final ComplianceClientRepository clients;

    @Transactional(readOnly = true)
    public List<AssignmentView> all(TenantId tenantId, String status, LocalDate today) {
        List<AssignmentView> out = new ArrayList<>();
        for (ComplianceProviderAssignment a : assignments.findAllForTenant(tenantId)) {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(a.getStatus())) continue;
            out.add(view(tenantId, a, today));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> forClient(TenantId tenantId, UUID clientId, LocalDate today) {
        clientName(tenantId, clientId);
        List<AssignmentView> out = new ArrayList<>();
        for (ComplianceProviderAssignment a : assignments.findForClient(tenantId, clientId)) out.add(view(tenantId, a, today));
        return out;
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> forProvider(TenantId tenantId, UUID providerId, LocalDate today) {
        findProvider(tenantId, providerId);
        List<AssignmentView> out = new ArrayList<>();
        for (ComplianceProviderAssignment a : assignments.findForProvider(tenantId, providerId)) out.add(view(tenantId, a, today));
        return out;
    }

    @Transactional(readOnly = true)
    public AssignmentDetail get(TenantId tenantId, UUID id, LocalDate today) {
        return detail(tenantId, find(tenantId, id), today);
    }

    @Transactional
    public AssignmentDetail propose(TenantId tenantId, UUID clientId, AssignmentRequest r, UUID by, LocalDate today) {
        clientName(tenantId, clientId);
        ComplianceProvider p = findProvider(tenantId, r.providerId());
        if (!p.isActive()) throw new HandyFlowException("This provider is inactive", HttpStatus.CONFLICT, "PROVIDER_INACTIVE");
        ComplianceProviderAssignment a = guard(() -> ComplianceProviderAssignment.propose(tenantId, r.providerId(), clientId, r.title(), r.description(), r.serviceCode(), r.dueDate(),
                r.accreditationRequired() == null || r.accreditationRequired(), r.waiverNote(), today, by));
        return detail(tenantId, assignments.save(a), today);
    }

    @Transactional
    public AssignmentDetail declare(TenantId tenantId, UUID id, boolean conflict, String note, UUID by, LocalDate today) {
        ComplianceProviderAssignment a = find(tenantId, id);
        guard(() -> { a.declare(conflict, note, by); return null; });
        return detail(tenantId, assignments.save(a), today);
    }

    @Transactional
    public AssignmentDetail approveConflict(TenantId tenantId, UUID id, String note, UUID by, LocalDate today) {
        ComplianceProviderAssignment a = find(tenantId, id);
        guard(() -> { a.approveConflict(by, note); return null; });
        return detail(tenantId, assignments.save(a), today);
    }

    @Transactional
    public AssignmentDetail activate(TenantId tenantId, UUID id, UUID by, LocalDate today) {
        ComplianceProviderAssignment a = find(tenantId, id);
        ComplianceProvider p = findProvider(tenantId, a.getProviderId());
        boolean usable = accreditations.findForProvider(tenantId, p.getId()).stream().anyMatch(x -> x.isUsable(today));
        guard(() -> { a.activate(by, p.isActive(), usable); return null; });
        return detail(tenantId, assignments.save(a), today);
    }

    @Transactional
    public AssignmentDetail complete(TenantId tenantId, UUID id, int rating, String note, UUID by, LocalDate today) {
        ComplianceProviderAssignment a = find(tenantId, id);
        List<String> statuses = new ArrayList<>();
        for (ComplianceProviderDeliverable d : deliverables.findForAssignment(tenantId, id)) statuses.add(d.getStatus());
        boolean done = ProviderWorkRules.deliverablesDone(statuses);
        guard(() -> { a.complete(by, rating, note, done, today); return null; });
        return detail(tenantId, assignments.save(a), today);
    }

    @Transactional
    public AssignmentDetail cancel(TenantId tenantId, UUID id, String note, UUID by, LocalDate today) {
        ComplianceProviderAssignment a = find(tenantId, id);
        guard(() -> { a.cancel(by, note); return null; });
        return detail(tenantId, assignments.save(a), today);
    }

    // ---- deliverables ----

    @Transactional
    public AssignmentDetail expect(TenantId tenantId, UUID assignmentId, DeliverableRequest r, LocalDate today) {
        ComplianceProviderAssignment a = find(tenantId, assignmentId);
        if (!a.isOpen()) throw new HandyFlowException("This assignment is " + a.getStatus().toLowerCase(), HttpStatus.CONFLICT, "PROVIDER_WORK_STATE");
        deliverables.save(guard(() -> ComplianceProviderDeliverable.expect(tenantId, assignmentId, r.title(), r.dueDate())));
        return detail(tenantId, a, today);
    }

    @Transactional
    public AssignmentDetail received(TenantId tenantId, UUID deliverableId, UUID by, LocalDate today) {
        ComplianceProviderDeliverable d = findDeliverable(tenantId, deliverableId);
        ComplianceProviderAssignment a = find(tenantId, d.getAssignmentId());
        if (!ProviderWorkRules.ACTIVE.equals(a.getStatus())) throw new HandyFlowException("Work has not started or has finished, so nothing can be received", HttpStatus.CONFLICT, "PROVIDER_WORK_STATE");
        guard(() -> { d.received(by); return null; });
        deliverables.save(d);
        return detail(tenantId, a, today);
    }

    @Transactional
    public AssignmentDetail review(TenantId tenantId, UUID deliverableId, boolean accepted, String note, UUID by, LocalDate today) {
        ComplianceProviderDeliverable d = findDeliverable(tenantId, deliverableId);
        guard(() -> { d.review(accepted, by, note); return null; });
        deliverables.save(d);
        return detail(tenantId, find(tenantId, d.getAssignmentId()), today);
    }

    @Transactional
    public AssignmentDetail waive(TenantId tenantId, UUID deliverableId, String note, UUID by, LocalDate today) {
        ComplianceProviderDeliverable d = findDeliverable(tenantId, deliverableId);
        guard(() -> { d.waive(by, note); return null; });
        deliverables.save(d);
        return detail(tenantId, find(tenantId, d.getAssignmentId()), today);
    }

    // ---- performance ----

    @Transactional(readOnly = true)
    public PerformanceView performance(TenantId tenantId, UUID providerId) {
        findProvider(tenantId, providerId);
        int completed = 0, onTime = 0, cancelled = 0, open = 0, rated = 0, sum = 0;
        for (ComplianceProviderAssignment a : assignments.findForProvider(tenantId, providerId)) {
            switch (a.getStatus()) {
                case "COMPLETED" -> { completed++; if (Boolean.TRUE.equals(a.getOnTime())) onTime++; if (a.getRating() != null) { rated++; sum += a.getRating(); } }
                case "CANCELLED" -> cancelled++;
                default -> open++;
            }
        }
        return new PerformanceView(providerId, completed, onTime, rated == 0 ? null : Math.round(sum * 10.0 / rated) / 10.0, cancelled, open);
    }

    // ---- internals ----

    private AssignmentDetail detail(TenantId tenantId, ComplianceProviderAssignment a, LocalDate today) {
        List<DeliverableView> ds = new ArrayList<>();
        for (ComplianceProviderDeliverable d : deliverables.findForAssignment(tenantId, a.getId())) {
            ds.add(new DeliverableView(d.getId(), d.getTitle(), d.getDueDate(), d.getStatus(), d.getReceivedBy(), d.getReceivedAt(), d.getReviewedBy(), d.getReviewedAt(), d.getReviewNote()));
        }
        return new AssignmentDetail(view(tenantId, a, today), ds);
    }

    private AssignmentView view(TenantId tenantId, ComplianceProviderAssignment a, LocalDate today) {
        ComplianceProvider p = providers.findOne(tenantId, a.getProviderId()).orElse(null);
        String problem = null;
        if (ProviderWorkRules.PROPOSED.equals(a.getStatus()) && p != null) {
            boolean usable = accreditations.findForProvider(tenantId, p.getId()).stream().anyMatch(x -> x.isUsable(today));
            problem = ProviderWorkRules.activationProblem(a.getStatus(), a.getConflictStatus(), a.isConflictApproved(), p.isActive(), a.isAccreditationRequired(), usable);
        }
        return new AssignmentView(a.getId(), a.getProviderId(), p == null ? null : p.getName(), a.getClientId(), clients.findByIdForTenant(tenantId, a.getClientId()).map(ComplianceClient::getName).orElse(null),
                a.getTitle(), a.getDescription(), a.getServiceCode(), a.getDueDate(), a.getStatus(), a.isAccreditationRequired(), a.getWaiverNote(), a.getConflictStatus(), a.getConflictNote(),
                a.getConflictBy(), a.isConflictApproved(), a.getConflictApprovedBy(), a.getConflictApprovalNote(), problem, a.getCompletedOn(), a.getRating(), a.getOnTime(), a.getCompletionNote(),
                a.getCancelNote(), a.getCreatedAt());
    }

    private String clientName(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).map(ComplianceClient::getName).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private ComplianceProvider findProvider(TenantId tenantId, UUID id) {
        return providers.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceProvider", id.toString()));
    }

    private ComplianceProviderAssignment find(TenantId tenantId, UUID id) {
        return assignments.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceProviderAssignment", id.toString()));
    }

    private ComplianceProviderDeliverable findDeliverable(TenantId tenantId, UUID id) {
        return deliverables.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceProviderDeliverable", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "PROVIDER_WORK_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "PROVIDER_WORK_INVALID");
        }
    }
}
