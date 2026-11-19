package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.BcTest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BcTestRepository extends JpaRepository<BcTest, UUID> {

    @Query("SELECT x FROM BcTest x WHERE x.tenantId = :tenantId AND x.planId IN :planIds ORDER BY x.testDate DESC, x.createdAt DESC")
    List<BcTest> findForPlans(TenantId tenantId, Collection<UUID> planIds);

    @Query("SELECT x FROM BcTest x WHERE x.tenantId = :tenantId ORDER BY x.testDate DESC, x.createdAt DESC")
    List<BcTest> findAllForTenant(TenantId tenantId);
}
