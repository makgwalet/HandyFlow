package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;

import java.util.Optional;
import java.util.UUID;

public interface ScSupplierRepository extends JpaRepository<ScSupplier, UUID> {

    @Query("SELECT s FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL")
    Page<ScSupplier> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT s FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL " +
            "AND s.status = :status")
    Page<ScSupplier> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId,
                                             @Param("status") String status, Pageable pageable);

    @Query("SELECT s FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL " +
            "AND (LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(s.contactEmail) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<ScSupplier> search(@Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);

    @Query("SELECT s FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<ScSupplier> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT COUNT(s) FROM ScSupplier s WHERE s.tenantId = :tenantId AND s.status = :status AND s.deletedAt IS NULL")
    long countByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);
}
