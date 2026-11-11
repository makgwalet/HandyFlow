package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.repository.WhseClientRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Client CRUD — direct structural mirror of CollAgencyClientService, minus
 * the trust-balance escape hatch (WhseClient carries no balance field: the
 * operator holds this client's GOODS, not their money, so there's nothing
 * here for a sibling service to mutate in-place the way
 * CollAgencyTrustTransactionService mutates CollAgencyClient.trustBalance).
 */
@Service
@RequiredArgsConstructor
public class WhseClientService {

    private final WhseClientRepository repository;
    private final WhseSettingsService settings;

    /** Optional so the services can be built without it; Spring injects it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WhsePackageService packages;

    private void requireRoom(TenantId tenantId, WhsePackageRules.Limit limit) {
        if (packages != null) packages.requireRoomFor(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public Page<WhseClient> list(TenantId tenantId, Pageable pageable) {
        return repository.findAllActive(tenantId.getValue(), pageable);
    }

    /** Unpaginated — used by dashboard/summary views and PDF exports. */
    @Transactional(readOnly = true)
    public List<WhseClient> listAll(TenantId tenantId) {
        return repository.findAllActiveList(tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public WhseClient get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public WhseClient create(TenantId tenantId, String tradingName, String registrationNumber,
                              BigDecimal storageRatePerUnitPerMonth, BigDecimal receivingFeePerUnit,
                              BigDecimal pickFeePerUnit, BigDecimal packFeePerOrder, String contactName,
                              String contactEmail, String contactPhone, String address) {
        settings.requireCanAddClient(tenantId); // an internal warehouse has one owner
        requireRoom(tenantId, WhsePackageRules.Limit.CLIENT_ACCOUNTS);
        WhseClient client = WhseClient.create(tenantId.getValue(), tradingName, registrationNumber,
                storageRatePerUnitPerMonth, receivingFeePerUnit, pickFeePerUnit, packFeePerOrder, contactName,
                contactEmail, contactPhone, address);
        return repository.save(client);
    }

    @Transactional
    public WhseClient update(TenantId tenantId, UUID id, String tradingName, String registrationNumber,
                              BigDecimal storageRatePerUnitPerMonth, BigDecimal receivingFeePerUnit,
                              BigDecimal pickFeePerUnit, BigDecimal packFeePerOrder, String contactName,
                              String contactEmail, String contactPhone, String address, String notes) {
        WhseClient client = findActive(tenantId, id);
        client.update(tradingName, registrationNumber, storageRatePerUnitPerMonth, receivingFeePerUnit,
                pickFeePerUnit, packFeePerOrder, contactName, contactEmail, contactPhone, address, notes);
        return repository.save(client);
    }

    @Transactional
    public WhseClient deactivate(TenantId tenantId, UUID id) {
        WhseClient client = findActive(tenantId, id);
        client.deactivate();
        return repository.save(client);
    }

    @Transactional
    public WhseClient reactivate(TenantId tenantId, UUID id) {
        WhseClient client = findActive(tenantId, id);
        client.reactivate();
        return repository.save(client);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        WhseClient client = findActive(tenantId, id);
        client.softDelete();
        repository.save(client);
    }

    WhseClient findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("WhseClient", id.toString()));
    }
}
