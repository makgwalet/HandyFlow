package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.InspectionTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTemplateRepository extends JpaRepository<InspectionTemplate, UUID> {

    @Query("SELECT t FROM InspectionTemplate t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL ORDER BY t.name")
    List<InspectionTemplate> findAllActive(TenantId tenantId);

    @Query("SELECT t FROM InspectionTemplate t WHERE t.tenantId = :tenantId AND t.id = :id AND t.deletedAt IS NULL")
    Optional<InspectionTemplate> findActiveById(TenantId tenantId, UUID id);

    /** Active templates that define a schedule — what the compliance board checks vehicles against. */
    @Query("SELECT t FROM InspectionTemplate t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL " +
            "AND t.active = true AND t.intervalDays IS NOT NULL")
    List<InspectionTemplate> findScheduled(TenantId tenantId);

    @Query("SELECT COUNT(t) > 0 FROM InspectionTemplate t WHERE t.tenantId = :tenantId " +
            "AND LOWER(t.name) = LOWER(:name) AND t.id <> :excludeId AND t.deletedAt IS NULL")
    boolean existsOtherWithName(TenantId tenantId, String name, UUID excludeId);
}