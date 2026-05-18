// ─── PATTERN 1: DIRECT CALL via Facade ───
// When: Module A needs data from Module B synchronously
// Example: InvoicingModule needs to verify a customer exists in CrmModule

// In crm/application/CrmFacade.java (PUBLIC interface)
package za.co.handyflow.platform.crm;

import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * WHY AN INTERFACE FACADE?
 *
 * The Facade is the ONLY public entry point to a module's services.
 * Other modules depend on the INTERFACE (not the implementation).
 *
 * This means:
 * 1. You can swap implementations without breaking callers
 * 2. The interface is the "contract" — it tells you exactly what
 *    this module offers to the outside world
 * 3. Easy to mock in tests
 */
public interface CrmFacade {
    Optional<CustomerSummary> findCustomerById(TenantId tenantId, UUID customerId);
    boolean customerExists(TenantId tenantId, UUID customerId);
}

