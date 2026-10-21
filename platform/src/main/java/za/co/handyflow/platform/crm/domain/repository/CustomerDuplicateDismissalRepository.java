package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CustomerDuplicateDismissal;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CustomerDuplicateDismissalRepository extends JpaRepository<CustomerDuplicateDismissal, UUID> {

    @Query("SELECT d.pairKey FROM CustomerDuplicateDismissal d WHERE d.tenantId = :tenantId")
    List<String> findPairKeys(@Param("tenantId") TenantId tenantId);
}
