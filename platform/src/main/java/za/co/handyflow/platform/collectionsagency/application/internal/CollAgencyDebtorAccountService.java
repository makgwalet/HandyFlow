package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyDebtorAccountRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollAgencyDebtorAccountService {

    private final CollAgencyDebtorAccountRepository repository;

    @Transactional(readOnly = true)
    public Page<CollAgencyDebtorAccount> listForClient(TenantId tenantId, UUID clientId, String status,
                                                        Pageable pageable) {
        return repository.findByClient(tenantId.getValue(), clientId, status, pageable);
    }

    /** Unpaginated — used by the client portfolio/recovery report and PDF export. */
    @Transactional(readOnly = true)
    public List<CollAgencyDebtorAccount> listAllForClient(TenantId tenantId, UUID clientId) {
        return repository.findAllActiveForClient(tenantId.getValue(), clientId);
    }

    @Transactional(readOnly = true)
    public CollAgencyDebtorAccount get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public CollAgencyDebtorAccount assign(TenantId tenantId, UUID id, UUID collectorId) {
        CollAgencyDebtorAccount account = findActive(tenantId, id);
        account.assign(collectorId);
        return repository.save(account);
    }

    @Transactional
    public CollAgencyDebtorAccount advanceStatus(TenantId tenantId, UUID id, String newStatus) {
        CollAgencyDebtorAccount account = findActive(tenantId, id);
        account.advanceStatus(newStatus);
        return repository.save(account);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        CollAgencyDebtorAccount account = findActive(tenantId, id);
        account.softDelete();
        repository.save(account);
    }

    CollAgencyDebtorAccount findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("CollAgencyDebtorAccount", id.toString()));
    }
}
