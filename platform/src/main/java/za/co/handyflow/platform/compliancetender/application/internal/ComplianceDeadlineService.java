package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceDeadline;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceDeadlineRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.compliancetender.dto.ComplianceDeadlineResponse;
import za.co.handyflow.platform.compliancetender.dto.CreateComplianceDeadlineRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceDeadlineService {

    private static final int DUE_SOON_DAYS = 14;

    private final ComplianceDeadlineRepository deadlineRepository;
    private final ComplianceRegistrationRepository registrationRepository;

    @Transactional(readOnly = true)
    public List<ComplianceDeadlineResponse> getPendingDeadlines(TenantId tenantId) {
        return deadlineRepository.findPendingForTenant(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ComplianceDeadlineResponse create(TenantId tenantId, CreateComplianceDeadlineRequest req, UUID createdBy) {
        if (req.registrationId() != null) {
            registrationRepository.findByIdForTenant(tenantId, req.registrationId())
                    .orElseThrow(() -> new ResourceNotFoundException("ComplianceRegistration", req.registrationId().toString()));
        }
        ComplianceDeadline deadline = ComplianceDeadline.create(tenantId, req.registrationId(), req.deadlineType(),
                req.description(), req.dueDate(), createdBy);
        deadlineRepository.save(deadline);
        log.info("Compliance deadline created id={} type={} due={} tenant={}",
                deadline.getId(), deadline.getDeadlineType(), deadline.getDueDate(), tenantId);
        return toResponse(deadline);
    }

    @Transactional
    public ComplianceDeadlineResponse markDone(TenantId tenantId, UUID id, UUID completedBy) {
        ComplianceDeadline deadline = find(tenantId, id);
        deadline.markDone(completedBy);
        deadlineRepository.save(deadline);
        log.info("Compliance deadline marked done id={} tenant={}", id, tenantId);
        return toResponse(deadline);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        deadlineRepository.delete(find(tenantId, id));
        log.info("Compliance deadline deleted id={} tenant={}", id, tenantId);
    }

    private ComplianceDeadline find(TenantId tenantId, UUID id) {
        return deadlineRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceDeadline", id.toString()));
    }

    private ComplianceDeadlineResponse toResponse(ComplianceDeadline d) {
        return new ComplianceDeadlineResponse(d.getId(), d.getRegistrationId(), d.getDeadlineType(),
                d.getDescription(), d.getDueDate(), d.getStatus(), d.isDueWithin(DUE_SOON_DAYS), d.getCompletedAt());
    }
}
