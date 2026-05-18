package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CrmFacadeImpl implements CrmFacade {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummary> findCustomerById(TenantId tenantId, UUID customerId) {
        return customerRepository.findActiveById(tenantId, customerId)
                .map(c -> new CustomerSummary(
                        c.getId(), c.getName(), c.getEmail(),
                        c.getPhone(), c.getTaxNumber()
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean customerExists(TenantId tenantId, UUID customerId) {
        return customerRepository.findActiveById(tenantId, customerId).isPresent();
    }
}