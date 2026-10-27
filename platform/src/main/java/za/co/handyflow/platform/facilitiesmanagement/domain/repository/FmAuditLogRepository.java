package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAuditLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface FmAuditLogRepository extends JpaRepository<FmAuditLog, UUID> {

    @Query("SELECT a FROM FmAuditLog a WHERE a.tenantId.value = :#{#tenantId.value} " +
            "AND (:entityType IS NULL OR a.entityType = :entityType) ORDER BY a.createdAt DESC")
    Page<FmAuditLog> findAllForTenant(@Param("tenantId") TenantId tenantId,
                                      @Param("entityType") String entityType, Pageable pageable);

    @Query("SELECT a FROM FmAuditLog a WHERE a.tenantId.value = :#{#tenantId.value} " +
            "AND a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt DESC")
    Page<FmAuditLog> findForEntity(@Param("tenantId") TenantId tenantId, @Param("entityType") String entityType,
                                   @Param("entityId") UUID entityId, Pageable pageable);
}