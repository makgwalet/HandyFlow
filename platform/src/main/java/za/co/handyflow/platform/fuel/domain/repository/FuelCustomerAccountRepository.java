package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelCustomerAccountRepository extends JpaRepository<FuelCustomerAccount, UUID> {

    @Query("SELECT a FROM FuelCustomerAccount a WHERE a.tenantId = :tenantId AND a.deletedAt IS NULL ORDER BY a.createdAt DESC")
    List<FuelCustomerAccount> findAllActive(TenantId tenantId);

    @Query("SELECT a FROM FuelCustomerAccount a WHERE a.tenantId = :tenantId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<FuelCustomerAccount> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT a FROM FuelCustomerAccount a WHERE a.tenantId = :tenantId AND a.crmCustomerId = :crmCustomerId AND a.deletedAt IS NULL")
    Optional<FuelCustomerAccount> findByCrmCustomerId(TenantId tenantId, UUID crmCustomerId);
}
