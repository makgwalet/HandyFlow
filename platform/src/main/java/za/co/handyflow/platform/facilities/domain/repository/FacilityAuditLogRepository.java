package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityAuditLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface FacilityAuditLogRepository extends JpaRepository<FacilityAuditLog, UUID> {

    @Query("SELECT a FROM FacilityAuditLog a WHERE a.tenantId.value = :#{#tenantId.value} " +
            "AND (:entityType IS NULL OR a.entityType = :entityType) ORDER BY a.createdAt DESC")
    Page<FacilityAuditLog> findAllForTenant(@Param("tenantId") TenantId tenantId,
                                            @Param("entityType") String entityType, Pageable pageable);

    @Query("SELECT a FROM FacilityAuditLog a WHERE a.tenantId.value = :#{#tenantId.value} " +
            "AND a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt DESC")
    Page<FacilityAuditLog> findForEntity(@Param("tenantId") TenantId tenantId, @Param("entityType") String entityType,
                                         @Param("entityId") UUID entityId, Pageable pageable);
}