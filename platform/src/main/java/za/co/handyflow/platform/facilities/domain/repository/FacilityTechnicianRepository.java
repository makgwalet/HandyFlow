package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnician;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityTechnicianRepository extends JpaRepository<FacilityTechnician, UUID> {

    @Query("SELECT t FROM FacilityTechnician t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id AND t.deletedAt IS NULL")
    Optional<FacilityTechnician> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM FacilityTechnician t WHERE t.tenantId = :#{#tenantId.value} AND t.deletedAt IS NULL")
    Page<FacilityTechnician> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    /** Active technician record(s) linked to a system user — drives "my assigned jobs" scope. */
    @Query("SELECT t.id FROM FacilityTechnician t WHERE t.tenantId = :#{#tenantId.value} " +
            "AND t.linkedUserId = :userId AND t.active = true AND t.deletedAt IS NULL")
    List<UUID> findIdsByLinkedUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId);

    /** Whether a DIFFERENT active technician is already linked to this user. */
    @Query("SELECT COUNT(t) > 0 FROM FacilityTechnician t WHERE t.tenantId = :#{#tenantId.value} " +
            "AND t.linkedUserId = :userId AND t.id <> :excludeId AND t.deletedAt IS NULL")
    boolean existsOtherLinkedToUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId,
                                    @Param("excludeId") UUID excludeId);
}