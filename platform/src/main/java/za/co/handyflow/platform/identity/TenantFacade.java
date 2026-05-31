package za.co.handyflow.platform.identity;

import za.co.handyflow.platform.shared.TenantId;
import java.util.Optional;

public interface TenantFacade {
    Optional<TenantDetails> findTenantDetails(TenantId tenantId);
}
