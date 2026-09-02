package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
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

    private static ContractSummary toSummary(Contract c) {
        return new ContractSummary(
                c.getId(), c.getContractNumber(), c.getTitle(), c.getContractType(), c.getStatus(),
                c.getStartDate(), c.getEndDate(), c.getValueAmount(), c.isAutoRenew());
    }
}
