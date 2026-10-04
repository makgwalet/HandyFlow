package za.co.handyflow.platform.supplychain.application;

import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of Supply Chain's suppliers for other modules (ADR-001, W7). Agriculture links stock receipts to a supplier through it and never touches Supply
 * Chain's repositories or entities.
 * <p>
 * It exposes IDENTITY ONLY: id, name and status. A supplier record also holds bank account details, contact details, VAT and BBBEE data and performance counters;
 * none of that crosses this boundary.
 */
public interface SupplierFacade {

    /** @param status ACTIVE, INACTIVE or BLACKLISTED */
    record SupplierSummary(UUID id, String name, String status) {
        public boolean isActive() { return "ACTIVE".equals(status); }
    }

    /** The tenant's ACTIVE suppliers by name; inactive and blacklisted ones are not offered for new purchases. At most {@code max} (capped at 500). */
    List<SupplierSummary> listActive(TenantId tenantId, int max);

    /** One supplier of any status, or empty if it does not exist for this tenant or was deleted. */
    Optional<SupplierSummary> find(TenantId tenantId, UUID supplierId);

    /** The suppliers among {@code ids} that exist for this tenant, by id, in one lookup. Ids that do not exist are simply absent. */
    Map<UUID, SupplierSummary> findAll(TenantId tenantId, Collection<UUID> ids);
}
