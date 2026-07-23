package za.co.handyflow.platform.ap.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.ap.domain.model.ApSupplierBanking;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApSupplierBankingRepository extends JpaRepository<ApSupplierBanking, UUID> {

    @Query("SELECT b FROM ApSupplierBanking b WHERE b.tenantId = :tenantId ORDER BY b.supplierName ASC")
    List<ApSupplierBanking> findAll(TenantId tenantId);

    Optional<ApSupplierBanking> findByIdAndTenantId(UUID id, TenantId tenantId);

    // Case-insensitive — the whole point is matching whatever casing a
    // bill's free-text supplierName happens to use, not requiring an
    // exact match.
    @Query("SELECT b FROM ApSupplierBanking b WHERE b.tenantId = :tenantId AND LOWER(b.supplierName) = LOWER(:supplierName)")
    Optional<ApSupplierBanking> findByTenantIdAndSupplierName(TenantId tenantId, String supplierName);

    boolean existsByTenantIdAndSupplierNameIgnoreCase(TenantId tenantId, String supplierName);
}