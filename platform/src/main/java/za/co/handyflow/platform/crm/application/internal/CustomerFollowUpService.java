package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.CustomerFollowUp;
import za.co.handyflow.platform.crm.domain.repository.CustomerFollowUpRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.CompleteFollowUpRequest;
import za.co.handyflow.platform.crm.dto.CreateFollowUpRequest;
import za.co.handyflow.platform.crm.dto.FollowUpResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerFollowUpService {

    private final CustomerFollowUpRepository followUpRepository;
    private final CustomerRepository         customerRepository;

    @Transactional
    public FollowUpResponse create(TenantId tenantId, UUID customerId,
                                   CreateFollowUpRequest req, UUID createdBy) {
        if (!customerRepository.existsActiveById(tenantId, customerId)) {
            throw new ResourceNotFoundException("Customer", customerId.toString());
        }
        var followUp = CustomerFollowUp.create(
                tenantId, customerId, req.dueDate(), req.note(), req.assignedTo(), createdBy);
        followUpRepository.save(followUp);
        log.info("[CRM] Follow-up created customer={} due={} assignedTo={} tenant={}",
                customerId, req.dueDate(), followUp.getAssignedTo(), tenantId);
        return toResponse(followUp);
    }

    @Transactional(readOnly = true)
    public List<FollowUpResponse> getForCustomer(TenantId tenantId, UUID customerId) {
        return followUpRepository.findByCustomer(tenantId, customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * FIX: "what if follow-ups were unsuccessful, rescheduled and all" —
     * completion now requires an outcome. RESCHEDULED specifically creates
     * a brand new follow-up (same customer, same note, same assignee, the
     * new due date) linked back via rescheduledFromId — a real chain of
     * attempts, not one row silently reopened and re-dated. The original
     * stays completed with outcome=RESCHEDULED; it's history, not undone.
     */
    @Transactional
    public FollowUpResponse complete(TenantId tenantId, UUID followUpId,
                                     CompleteFollowUpRequest req, UUID completedBy) {
        var followUp = findOrThrow(tenantId, followUpId);
        followUp.complete(req.outcome(), completedBy);

        if (req.outcome() == CustomerFollowUp.Outcome.RESCHEDULED) {
            if (req.rescheduleDate() == null) {
                throw new IllegalArgumentException("A new due date is required when rescheduling");
            }
            var next = CustomerFollowUp.create(
                    tenantId, followUp.getCustomerId(), req.rescheduleDate(),
                    followUp.getNote(), followUp.getAssignedTo(), completedBy,
                    followUp.getId());
            followUpRepository.save(next);
            log.info("[CRM] Follow-up rescheduled customer={} from={} to new due={} tenant={}",
                    followUp.getCustomerId(), followUp.getId(), req.rescheduleDate(), tenantId);
        }

        return toResponse(followUp);
    }

    @Transactional
    public FollowUpResponse reopen(TenantId tenantId, UUID followUpId) {
        var followUp = findOrThrow(tenantId, followUpId);
        followUp.reopen();
        return toResponse(followUp);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID followUpId) {
        var followUp = findOrThrow(tenantId, followUpId);
        followUpRepository.delete(followUp);
    }

    private CustomerFollowUp findOrThrow(TenantId tenantId, UUID followUpId) {
        return followUpRepository.findByIdAndTenant(tenantId, followUpId)
                .orElseThrow(() -> new ResourceNotFoundException("FollowUp", followUpId.toString()));
    }

    private FollowUpResponse toResponse(CustomerFollowUp f) {
        return new FollowUpResponse(
                f.getId(), f.getCustomerId(), f.getDueDate(), f.getNote(),
                f.getAssignedTo(), f.isCompleted(), f.getCompletedAt(),
                f.isOverdue(), f.getCreatedAt(),
                f.getOutcome() != null ? f.getOutcome().name() : null,
                f.getRescheduledFromId()
        );
    }
}