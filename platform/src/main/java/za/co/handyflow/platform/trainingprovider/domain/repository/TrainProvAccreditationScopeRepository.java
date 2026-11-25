package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditationScope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAccreditationScopeRepository extends JpaRepository<TrainProvAccreditationScope, UUID> {

    @Query("SELECT s FROM TrainProvAccreditationScope s WHERE s.tenantId = :#{#tenantId.value} AND s.accreditationId = :accreditationId ORDER BY s.status, s.programmeTitle")
    List<TrainProvAccreditationScope> findForAccreditation(TenantId tenantId, UUID accreditationId);

    @Query("SELECT s FROM TrainProvAccreditationScope s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id")
    Optional<TrainProvAccreditationScope> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT s FROM TrainProvAccreditationScope s WHERE s.tenantId = :#{#tenantId.value} AND s.courseId = :courseId")
    List<TrainProvAccreditationScope> findForCourse(TenantId tenantId, UUID courseId);

    @Query("""
        SELECT COUNT(s) > 0 FROM TrainProvAccreditationScope s
        WHERE s.tenantId = :#{#tenantId.value} AND s.accreditationId = :accreditationId AND s.siteId = :siteId
        AND s.status = 'ACTIVE' AND LOWER(s.programmeTitle) = LOWER(:title)
        AND COALESCE(LOWER(s.qualificationRef), '') = COALESCE(LOWER(:ref), '')
        """)
    boolean liveDuplicate(TenantId tenantId, UUID accreditationId, UUID siteId, String title, String ref);

    @Query("SELECT COUNT(s) FROM TrainProvAccreditationScope s WHERE s.tenantId = :#{#tenantId.value} AND s.siteId = :siteId AND s.status = 'ACTIVE'")
    long countLiveForSite(TenantId tenantId, UUID siteId);
}
