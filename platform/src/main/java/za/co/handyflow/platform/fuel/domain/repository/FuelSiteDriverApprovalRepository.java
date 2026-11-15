package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSiteDriverApproval;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelSiteDriverApprovalRepository extends JpaRepository<FuelSiteDriverApproval, UUID> {

    @Query("SELECT a FROM FuelSiteDriverApproval a WHERE a.tenantId = :tenantId AND a.siteId = :siteId")
    List<FuelSiteDriverApproval> findForSite(TenantId tenantId, UUID siteId);

    @Query("SELECT a FROM FuelSiteDriverApproval a WHERE a.tenantId = :tenantId AND a.siteId = :siteId AND a.employeeId = :employeeId")
    Optional<FuelSiteDriverApproval> findForDriver(TenantId tenantId, UUID siteId, UUID employeeId);
}
