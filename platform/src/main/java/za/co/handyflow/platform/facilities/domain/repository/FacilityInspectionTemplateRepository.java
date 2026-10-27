package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspectionTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityInspectionTemplateRepository extends JpaRepository<FacilityInspectionTemplate, UUID> {

    @Query("SELECT t FROM FacilityInspectionTemplate t WHERE t.tenantId.value = :#{#tenantId.value} AND t.id = :id")
    Optional<FacilityInspectionTemplate> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM FacilityInspectionTemplate t WHERE t.tenantId.value = :#{#tenantId.value} " +
            "AND (:includeInactive = true OR t.active = true) ORDER BY t.name ASC")
    List<FacilityInspectionTemplate> findAllForTenant(@Param("tenantId") TenantId tenantId,
                                                      @Param("includeInactive") boolean includeInactive);

    @Query("SELECT COUNT(t) > 0 FROM FacilityInspectionTemplate t WHERE t.tenantId.value = :#{#tenantId.value} " +
            "AND LOWER(t.name) = LOWER(:name) AND t.id <> :excludeId")
    boolean existsByName(@Param("tenantId") TenantId tenantId, @Param("name") String name, @Param("excludeId") UUID excludeId);
}
