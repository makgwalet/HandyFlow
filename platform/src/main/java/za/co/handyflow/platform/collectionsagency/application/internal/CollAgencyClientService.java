package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyClientRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollAgencyClientService {

    private final CollAgencyClientRepository repository;

    @Transactional(readOnly = true)
    public Page<CollAgencyClient> list(TenantId tenantId, Pageable pageable) {
        return repository.findAllActive(tenantId.getValue(), pageable);
    }

    /** Unpaginated — used by dashboard/summary views and PDF exports. */
    @Transactional(readOnly = true)
    public List<CollAgencyClient> listAll(TenantId tenantId) {
        return repository.findAllActiveList(tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public CollAgencyClient get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public CollAgencyClient create(TenantId tenantId, String tradingName, String registrationNumber,
                                    BigDecimal commissionRatePct, String contactName, String contactEmail,
                                    String contactPhone, String address) {
        CollAgencyClient client = CollAgencyClient.create(tenantId.getValue(), tradingName, registrationNumber,
                commissionRatePct, contactName, contactEmail, contactPhone, address);
        return repository.save(client);
    }

    @Transactional
    public CollAgencyClient update(TenantId tenantId, UUID id, String tradingName, String registrationNumber,
                                    BigDecimal commissionRatePct, String contactName, String contactEmail,
                                    String contactPhone, String address, String notes) {
        CollAgencyClient client = findActive(tenantId, id);
        client.update(tradingName, registrationNumber, commissionRatePct, contactName, contactEmail, contactPhone,
                address, notes);
        return repository.save(client);
    }

    @Transactional
    public CollAgencyClient deactivate(TenantId tenantId, UUID id) {
        CollAgencyClient client = findActive(tenantId, id);
        client.deactivate();
        return repository.save(client);
    }

    @Transactional
    public CollAgencyClient reactivate(TenantId tenantId, UUID id) {
        CollAgencyClient client = findActive(tenantId, id);
        client.reactivate();
        return repository.save(client);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        CollAgencyClient client = findActive(tenantId, id);
        client.softDelete();
        repository.save(client);
    }

    /**
     * Persists a client entity whose trustBalance has already been mutated
     * in-process via {@link CollAgencyClient#increaseTrustBalance(BigDecimal)}
     * or {@link CollAgencyClient#decreaseTrustBalance(BigDecimal)}.
     * <p>
     * Package-private and deliberately narrow: this is NOT a general-purpose
     * "save whatever you've changed" escape hatch for other services to use
     * on arbitrary client mutations — those go through the public
     * create/update/deactivate/reactivate/delete methods above, which own
     * their own validation and intent. This method exists solely because
     * {@link CollAgencyTrustTransactionService} (same package) mutates the
     * trust balance as part of its own transactional unit of work
     * (recordDebtorPayment / processRemittance) and needs to persist that
     * one field change without re-running client-update validation that has
     * nothing to do with trust accounting.
     */
    @Transactional
    CollAgencyClient saveTrustBalanceChange(CollAgencyClient client) {
        return repository.save(client);
    }

    CollAgencyClient findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("CollAgencyClient", id.toString()));
    }
}
