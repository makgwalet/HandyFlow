package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelUserScope;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelUserScopeRepository extends JpaRepository<FuelUserScope, UUID> {

    @Query("SELECT s FROM FuelUserScope s WHERE s.tenantId = :tenantId AND s.userId = :userId")
    List<FuelUserScope> findByUser(TenantId tenantId, UUID userId);

    @Query("SELECT s FROM FuelUserScope s WHERE s.tenantId = :tenantId ORDER BY s.userId, s.createdAt")
    List<FuelUserScope> findAllForTenant(TenantId tenantId);

    @Modifying
    @Query("DELETE FROM FuelUserScope s WHERE s.tenantId = :tenantId AND s.userId = :userId")
    int deleteByUser(TenantId tenantId, UUID userId);
}
