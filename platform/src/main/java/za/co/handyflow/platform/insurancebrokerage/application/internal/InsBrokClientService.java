package za.co.handyflow.platform.insurancebrokerage.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokClient;
import za.co.handyflow.platform.insurancebrokerage.domain.repository.InsBrokClientRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsBrokClientService {

    private final InsBrokClientRepository repository;

    @Transactional(readOnly = true)
    public Page<InsBrokClient> list(TenantId tenantId, Pageable pageable) {
        return repository.findAllActive(tenantId.getValue(), pageable);
    }

    @Transactional(readOnly = true)
    public List<InsBrokClient> listAll(TenantId tenantId) {
        return repository.findAllActiveList(tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public InsBrokClient get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public InsBrokClient create(TenantId tenantId, String clientName, String clientType,
                                 String registrationOrIdNumber, String contactName, String contactEmail,
                                 String contactPhone, String address, BigDecimal defaultCommissionRatePct,
                                 String notes) {
        InsBrokClient client = InsBrokClient.create(tenantId.getValue(), clientName, clientType,
                registrationOrIdNumber, contactName, contactEmail, contactPhone, address,
                defaultCommissionRatePct, notes);
        return repository.save(client);
    }

    @Transactional
    public InsBrokClient update(TenantId tenantId, UUID id, String clientName, String clientType,
                                 String registrationOrIdNumber, String contactName, String contactEmail,
                                 String contactPhone, String address, BigDecimal defaultCommissionRatePct,
                                 String notes) {
        InsBrokClient client = findActive(tenantId, id);
        client.update(clientName, clientType, registrationOrIdNumber, contactName, contactEmail, contactPhone,
                address, defaultCommissionRatePct, notes);
        return repository.save(client);
    }

    @Transactional
    public InsBrokClient deactivate(TenantId tenantId, UUID id) {
        InsBrokClient client = findActive(tenantId, id);
        client.deactivate();
        return repository.save(client);
    }

    @Transactional
    public InsBrokClient reactivate(TenantId tenantId, UUID id) {
        InsBrokClient client = findActive(tenantId, id);
        client.reactivate();
        return repository.save(client);
    }

    /** Package-private: read by InsBrokCommissionInvoiceService to resolve the default commission rate. */
    InsBrokClient findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("InsBrokClient", id.toString()));
    }
}
