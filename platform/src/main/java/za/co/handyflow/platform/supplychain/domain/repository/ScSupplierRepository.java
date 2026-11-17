package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScSupplierRepository extends JpaRepository<ScSupplier, UUID> {

    @Query("SELECT s FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL ORDER BY s.name")
    Page<ScSupplier> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT s FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL AND s.status = :status ORDER BY s.name")
    Page<ScSupplier> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId,
                                             @Param("status") SupplierStatus status,
                                             Pageable pageable);

    @Query("SELECT s FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL " +
            "AND (LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(s.contactEmail) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<ScSupplier> search(@Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);

    @Query("SELECT s FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<ScSupplier> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT COUNT(s) FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.status = :status AND s.deletedAt IS NULL")
    long countByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") SupplierStatus status);

    /** NEW (chunk 4): fraud signal — the same bank account on another live supplier record. */
    @Query("SELECT COUNT(s) FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.bankAccount = :account AND s.id <> :excludeId AND s.deletedAt IS NULL")
    long countOtherWithBankAccount(@Param("tenantId") UUID tenantId,
                                   @Param("account") String account,
                                   @Param("excludeId") UUID excludeId);

    /**
     * Cross-tenant query for the weekly BBBEE certificate expiry digest.
     * Returns [tenant_id, supplier_id, name, bbbee_expiry].
     */
    @Query(value = """
            SELECT s.tenant_id, s.id, s.name, s.bbbee_expiry
            FROM sc_suppliers s
            WHERE s.deleted_at IS NULL
              AND s.bbbee_expiry IS NOT NULL
              AND s.bbbee_expiry >= CURRENT_DATE
              AND s.bbbee_expiry <= CURRENT_DATE + INTERVAL '30 days'
            ORDER BY s.tenant_id, s.bbbee_expiry
            """, nativeQuery = true)
    List<Object[]> findExpiringBbbeeCertificates();
}
