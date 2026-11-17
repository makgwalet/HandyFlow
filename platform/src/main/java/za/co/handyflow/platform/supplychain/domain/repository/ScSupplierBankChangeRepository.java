package za.co.handyflow.platform.supplychain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierBankChange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScSupplierBankChangeRepository extends JpaRepository<ScSupplierBankChange, UUID> {

    @Query("SELECT c FROM ScSupplierBankChange c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<ScSupplierBankChange> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Locked so two verifiers can't both apply the same change. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ScSupplierBankChange c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<ScSupplierBankChange> lockByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM ScSupplierBankChange c WHERE c.tenantId = :tenantId AND c.supplierId = :supplierId ORDER BY c.createdAt DESC")
    List<ScSupplierBankChange> findAllForSupplier(@Param("tenantId") UUID tenantId, @Param("supplierId") UUID supplierId);

    @Query("SELECT c FROM ScSupplierBankChange c WHERE c.tenantId = :tenantId AND c.supplierId = :supplierId AND c.status = 'PENDING_VERIFICATION'")
    Optional<ScSupplierBankChange> findOpenForSupplier(@Param("tenantId") UUID tenantId, @Param("supplierId") UUID supplierId);
}
