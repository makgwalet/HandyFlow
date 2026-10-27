package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspection;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityInspectionRepository extends JpaRepository<FacilityInspection, UUID> {

    @Query("SELECT i FROM FacilityInspection i WHERE i.tenantId = :#{#tenantId.value} AND i.id = :id")
    Optional<FacilityInspection> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** Scope-aware list. {@code siteIds} must never be empty; null filters are ignored. */
    @Query("SELECT i FROM FacilityInspection i WHERE i.tenantId = :#{#tenantId.value} " +
            "AND (:siteId IS NULL OR i.siteId = :siteId) AND (:assetId IS NULL OR i.assetId = :assetId) " +
            "AND (:status IS NULL OR i.status = :status) " +
            "AND (:allSites = true OR i.siteId IN :siteIds) ORDER BY i.startedAt DESC")
    Page<FacilityInspection> findAllInScope(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId,
                                            @Param("assetId") UUID assetId, @Param("status") String status,
                                            @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds,
                                            Pageable pageable);

    /** Failed inspections completed since {@code since}, within site scope — for the action centre. */
    @Query("SELECT i FROM FacilityInspection i WHERE i.tenantId = :#{#tenantId.value} AND i.status = 'COMPLETED' " +
            "AND i.result = 'FAIL' AND i.completedAt >= :since AND (:allSites = true OR i.siteId IN :siteIds) " +
            "ORDER BY i.completedAt DESC")
    List<FacilityInspection> findRecentFailuresInScope(@Param("tenantId") TenantId tenantId, @Param("since") Instant since,
                                                       @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds);

    @Query("SELECT COUNT(i) FROM FacilityInspection i WHERE i.tenantId = :#{#tenantId.value} AND i.templateId = :templateId")
    long countForTemplate(@Param("tenantId") TenantId tenantId, @Param("templateId") UUID templateId);
}
