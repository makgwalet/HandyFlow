package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmTechnician;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmTechnicianRepository extends JpaRepository<FmTechnician, UUID> {

    @Query("SELECT t FROM FmTechnician t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id AND t.deletedAt IS NULL")
    Optional<FmTechnician> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM FmTechnician t WHERE t.tenantId = :#{#tenantId.value} AND t.deletedAt IS NULL")
    Page<FmTechnician> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    /** Active technician record(s) linked to a system user — drives "my assigned jobs" scope. */
    @Query("SELECT t.id FROM FmTechnician t WHERE t.tenantId = :#{#tenantId.value} " +
            "AND t.linkedUserId = :userId AND t.active = true AND t.deletedAt IS NULL")
    List<UUID> findIdsByLinkedUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId);

    /** Whether a DIFFERENT technician is already linked to this user. */
    @Query("SELECT COUNT(t) > 0 FROM FmTechnician t WHERE t.tenantId = :#{#tenantId.value} " +
            "AND t.linkedUserId = :userId AND t.id <> :excludeId AND t.deletedAt IS NULL")
    boolean existsOtherLinkedToUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId,
                                    @Param("excludeId") UUID excludeId);
}