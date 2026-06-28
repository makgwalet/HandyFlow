package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.repository.Customer360Repository;
import za.co.handyflow.platform.crm.domain.repository.Customer360Repository.Customer360Summary;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Customer360Service — fetches the cross-module 360 summary.
 *
 * Deliberately kept thin: verify the customer belongs to this tenant,
 * query the view via Customer360Repository, return the result.
 *
 * WHY validate customer existence before querying the view?
 * The view only contains active (non-deleted) customers.  If a deleted
 * customer's ID is passed, the view returns nothing and we'd return
 * an all-zeros summary with no error — misleading.  The existence check
 * ensures we 404 correctly for unknown or deleted customers.
 */
@Service
@RequiredArgsConstructor
public class Customer360Service {

    private final CustomerRepository   customerRepository;
    private final Customer360Repository repository360;

    @Transactional(readOnly = true)
    public Customer360Summary get360(TenantId tenantId, UUID customerId) {
        // Verify customer belongs to this tenant — prevents cross-tenant data leaks
        if (!customerRepository.existsActiveById(tenantId, customerId)) {
            throw new ResourceNotFoundException("Customer", customerId.toString());
        }

        // Graceful zero-return if bookings/invoices modules not yet integrated:
        // the view LEFT JOINs, so a customer with no bookings/invoices returns
        // a valid row with all counts = 0.  Optional.empty() only happens if
        // the customer somehow isn't in the view (shouldn't occur for active customers).
        return repository360.find360Summary(tenantId, customerId)
                .orElseGet(() -> new Customer360Summary(
                        customerId, 0, 0, null, 0,
                        BigDecimal.ZERO, 0, BigDecimal.ZERO
                ));
    }
}