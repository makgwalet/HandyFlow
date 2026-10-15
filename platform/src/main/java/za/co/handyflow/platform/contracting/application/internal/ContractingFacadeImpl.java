package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractTemplateRepository;
import za.co.handyflow.platform.contracting.dto.CreateContractRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin adapter over the real ContractRepository — deliberately does NOT go
 * through ContractingService, matching EvidenceFacade/CrmFacade's own
 * "thin pass-through, no new logic" convention. ContractRepository's query
 * methods (findAllActive/findActiveById/findSignedExpiringWithin) are
 * confirmed directly against the real interface — same repository
 * ContractExpiryScheduler already uses for the identical
 * expiring-within-N-days query, not a re-derived one.
 */
@Service
@RequiredArgsConstructor
public class ContractingFacadeImpl implements ContractingFacade {

    private final ContractRepository contractRepo;
    private final ContractTemplateRepository templateRepo;
    private final ContractingService contracting;

    @Override
    @Transactional(readOnly = true)
    public List<ContractSummary> listAll(TenantId tenantId) {
        return contractRepo.findAllActive(tenantId, null, null, Pageable.unpaged())
                .map(ContractingFacadeImpl::toSummary)
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContractSummary> findById(TenantId tenantId, UUID contractId) {
        return contractRepo.findActiveById(tenantId, contractId).map(ContractingFacadeImpl::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractSummary> listExpiringWithin(TenantId tenantId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(days);
        return contractRepo.findSignedExpiringWithin(tenantId, today, cutoff).stream()
                .map(ContractingFacadeImpl::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public ContractSummary createDraft(TenantId tenantId, DraftContract d, UUID createdByUserId) {
        contracting.seedTemplates(tenantId);
        UUID templateId = d.body() != null ? null
                : templateRepo.findActiveByType(tenantId, d.contractType()).stream().findFirst().map(t -> t.getId()).orElse(null);
        var created = contracting.createContract(tenantId, new CreateContractRequest(d.title(), d.contractType(), templateId, d.body(),
                d.variables(), d.valueAmount(), d.startDate(), d.endDate(), false, null, d.notes()), createdByUserId);
        Contract c = contractRepo.findActiveById(tenantId, created.id())
                .orElseThrow(() -> new ResourceNotFoundException("Contract", String.valueOf(created.id())));
        c.linkReference(d.refType(), d.refId(), d.parentContractId());
        contractRepo.save(c);
        return toSummary(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractSummary> findByReference(TenantId tenantId, String refType, UUID refId) {
        return contractRepo.findByReference(tenantId, refType, refId).stream().map(ContractingFacadeImpl::toSummary).toList();
    }

    private static ContractSummary toSummary(Contract c) {
        return new ContractSummary(
                c.getId(), c.getContractNumber(), c.getTitle(), c.getContractType(), c.getStatus(),
                c.getStartDate(), c.getEndDate(), c.getValueAmount(), c.isAutoRenew(), c.getParentContractId());
    }
}
