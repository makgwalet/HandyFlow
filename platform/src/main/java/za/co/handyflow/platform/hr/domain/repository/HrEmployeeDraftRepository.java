package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeDraft;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeDraftRepository extends JpaRepository<HrEmployeeDraft, UUID> {

    @Query("SELECT d FROM HrEmployeeDraft d WHERE d.tenantId = :#{#tenantId.value} AND d.ownerId = :ownerId ORDER BY d.updatedAt DESC")
    List<HrEmployeeDraft> mine(TenantId tenantId, UUID ownerId);

    @Query("SELECT d FROM HrEmployeeDraft d WHERE d.tenantId = :#{#tenantId.value} AND d.ownerId = :ownerId AND d.id = :id")
    Optional<HrEmployeeDraft> mineById(TenantId tenantId, UUID ownerId, UUID id);

    @Modifying
    @Query("DELETE FROM HrEmployeeDraft d WHERE d.tenantId = :#{#tenantId.value} AND d.updatedAt < :before")
    int deleteOlderThan(TenantId tenantId, Instant before);
}
