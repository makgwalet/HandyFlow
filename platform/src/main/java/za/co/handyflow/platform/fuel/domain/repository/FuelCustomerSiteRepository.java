package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelCustomerSiteRepository extends JpaRepository<FuelCustomerSite, UUID> {

    @Query("SELECT s FROM FuelCustomerSite s WHERE s.tenantId = :tenantId AND s.accountId = :accountId AND s.deletedAt IS NULL ORDER BY s.name")
    List<FuelCustomerSite> findByAccount(TenantId tenantId, UUID accountId);

    @Query("SELECT s FROM FuelCustomerSite s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<FuelCustomerSite> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT s.accountId, COUNT(s) FROM FuelCustomerSite s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL GROUP BY s.accountId")
    List<Object[]> countByAccount(TenantId tenantId);
}
