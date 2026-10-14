package za.co.handyflow.platform.auditor.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditShareRepository extends JpaRepository<AuditShare, UUID> {

    @Query("SELECT s FROM AuditShare s WHERE s.tenantId = :tenantId AND s.itemType = :itemType AND s.withdrawnAt IS NULL")
    List<AuditShare> findLive(@Param("tenantId") UUID tenantId, @Param("itemType") String itemType);

    @Query("SELECT s FROM AuditShare s WHERE s.tenantId = :tenantId AND s.itemType = :itemType AND s.itemId = :itemId AND s.withdrawnAt IS NULL")
    Optional<AuditShare> findLiveFor(@Param("tenantId") UUID tenantId, @Param("itemType") String itemType, @Param("itemId") UUID itemId);

    @Query("SELECT s FROM AuditShare s WHERE s.tenantId = :tenantId ORDER BY s.sharedAt DESC")
    List<AuditShare> findAllForTenant(@Param("tenantId") UUID tenantId);

    Optional<AuditShare> findByTenantIdAndId(UUID tenantId, UUID id);
}
