package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplierBankAccount;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelSupplierBankAccountRepository extends JpaRepository<FuelSupplierBankAccount, UUID> {

    @Query("SELECT a FROM FuelSupplierBankAccount a WHERE a.tenantId = :tenantId AND a.supplierId = :supplierId ORDER BY a.proposedAt DESC")
    List<FuelSupplierBankAccount> findBySupplier(TenantId tenantId, UUID supplierId);

    @Query("SELECT a FROM FuelSupplierBankAccount a WHERE a.tenantId = :tenantId AND a.supplierId = :supplierId AND a.id = :id")
    Optional<FuelSupplierBankAccount> findOne(TenantId tenantId, UUID supplierId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FuelSupplierBankAccount a WHERE a.tenantId = :tenantId AND a.supplierId = :supplierId AND a.id = :id")
    Optional<FuelSupplierBankAccount> findOneForUpdate(TenantId tenantId, UUID supplierId, UUID id);

    @Query("SELECT a FROM FuelSupplierBankAccount a WHERE a.tenantId = :tenantId AND a.supplierId = :supplierId AND a.status = 'VERIFIED'")
    Optional<FuelSupplierBankAccount> findVerified(TenantId tenantId, UUID supplierId);

    @Query("SELECT a FROM FuelSupplierBankAccount a WHERE a.tenantId = :tenantId AND a.supplierId = :supplierId AND a.status = 'PENDING_VERIFICATION'")
    Optional<FuelSupplierBankAccount> findPending(TenantId tenantId, UUID supplierId);
}
