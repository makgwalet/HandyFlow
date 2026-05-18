package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.CreateCustomerRequest;
import za.co.handyflow.platform.crm.dto.CustomerResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomers(TenantId tenantId,
                                               String search,
                                               Pageable pageable) {
        var page = (search == null || search.isBlank())
                ? customerRepository.findAllActive(tenantId, pageable)
                : customerRepository.searchActive(tenantId, search, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(TenantId tenantId, UUID id) {
        return customerRepository.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));
    }

    @Transactional
    public CustomerResponse createCustomer(TenantId tenantId,
                                           CreateCustomerRequest request) {
        if (request.email() != null &&
                customerRepository.existsByTenantIdAndEmailAndDeletedAtIsNull(
                        tenantId, request.email())) {
            throw new IllegalArgumentException(
                    "A customer with email '" + request.email() + "' already exists"
            );
        }

        Customer customer = Customer.create(
                tenantId, request.name(), request.email(),
                request.phone(), request.address(), request.taxNumber()
        );
        customerRepository.save(customer);
        log.info("Created customer={} tenant={}", customer.getName(), tenantId);
        return toResponse(customer);
    }

    @Transactional
    public void softDeleteCustomer(TenantId tenantId, UUID id) {
        Customer customer = customerRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));
        customer.softDelete(null);
        customerRepository.save(customer);
        log.info("Soft deleted customer={} tenant={}", id, tenantId);
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getName(), c.getEmail(), c.getPhone(),
                c.getAddress(), c.getTaxNumber(), c.getNotes(), c.getCreatedAt()
        );
    }
}
