package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityCorrectiveAction;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityCorrectiveActionRepository extends JpaRepository<FacilityCorrectiveAction, UUID> {

    @Query("SELECT a FROM FacilityCorrectiveAction a WHERE a.tenantId.value = :#{#tenantId.value} AND a.id = :id")
    Optional<FacilityCorrectiveAction> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** Scope-aware list, most urgent due date first. {@code siteIds} must never be empty; null filters are ignored. */
    @Query("SELECT a FROM FacilityCorrectiveAction a WHERE a.tenantId.value = :#{#tenantId.value} " +
            "AND (:siteId IS NULL OR a.siteId = :siteId) AND (:status IS NULL OR a.status = :status) " +
            "AND (:allSites = true OR a.siteId IN :siteIds) ORDER BY a.dueDate ASC, a.createdAt ASC")
    Page<FacilityCorrectiveAction> findAllInScope(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId,
                                                  @Param("status") String status, @Param("allSites") boolean allSites,
                                                  @Param("siteIds") Collection<UUID> siteIds, Pageable pageable);

    /** Every unfinished action in scope — feeds the action centre. */
    @Query("SELECT a FROM FacilityCorrectiveAction a WHERE a.tenantId.value = :#{#tenantId.value} " +
            "AND a.status IN ('OPEN','IN_PROGRESS') AND (:allSites = true OR a.siteId IN :siteIds) ORDER BY a.dueDate ASC")
    List<FacilityCorrectiveAction> findUnfinishedInScope(@Param("tenantId") TenantId tenantId,
                                                         @Param("allSites") boolean allSites,
                                                         @Param("siteIds") Collection<UUID> siteIds);

    @Query("SELECT a FROM FacilityCorrectiveAction a WHERE a.tenantId.value = :#{#tenantId.value} AND a.sourceType = :sourceType " +
            "AND a.sourceId = :sourceId ORDER BY a.createdAt ASC")
    List<FacilityCorrectiveAction> findBySource(@Param("tenantId") TenantId tenantId, @Param("sourceType") String sourceType,
                                                @Param("sourceId") UUID sourceId);

    @Query("SELECT COUNT(a) FROM FacilityCorrectiveAction a WHERE a.tenantId.value = :#{#tenantId.value} AND a.assetId = :assetId " +
            "AND a.status IN ('OPEN','IN_PROGRESS')")
    long countUnfinishedForAsset(@Param("tenantId") TenantId tenantId, @Param("assetId") UUID assetId);
}
