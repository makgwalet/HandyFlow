package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.FleetIssue;
import za.co.handyflow.platform.fleet.domain.model.IssueStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface FleetIssueRepository extends JpaRepository<FleetIssue, UUID> {

    @Query("SELECT i FROM FleetIssue i WHERE i.tenantId = :tenantId AND i.id = :id AND i.deletedAt IS NULL")
    Optional<FleetIssue> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT i FROM FleetIssue i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL ORDER BY i.createdAt DESC")
    Page<FleetIssue> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT i FROM FleetIssue i WHERE i.tenantId = :tenantId AND i.status = :status AND i.deletedAt IS NULL ORDER BY i.createdAt DESC")
    Page<FleetIssue> findByStatus(TenantId tenantId, IssueStatus status, Pageable pageable);

    @Query("SELECT i FROM FleetIssue i WHERE i.tenantId = :tenantId AND i.vehicleId = :vehicleId AND i.deletedAt IS NULL ORDER BY i.createdAt DESC")
    Page<FleetIssue> findByVehicle(TenantId tenantId, UUID vehicleId, Pageable pageable);

    @Query("SELECT i FROM FleetIssue i WHERE i.tenantId = :tenantId AND i.vehicleId = :vehicleId AND i.status = :status AND i.deletedAt IS NULL ORDER BY i.createdAt DESC")
    Page<FleetIssue> findByVehicleAndStatus(TenantId tenantId, UUID vehicleId, IssueStatus status, Pageable pageable);

    @Query("SELECT COUNT(i) FROM FleetIssue i WHERE i.tenantId = :tenantId AND i.vehicleId = :vehicleId " +
            "AND i.status IN :statuses AND i.deletedAt IS NULL")
    long countByVehicleAndStatuses(TenantId tenantId, UUID vehicleId, Collection<IssueStatus> statuses);

    /** Used to avoid raising a second issue for a defect that is already open on the same vehicle. */
    @Query("SELECT COUNT(i) > 0 FROM FleetIssue i WHERE i.tenantId = :tenantId AND i.vehicleId = :vehicleId " +
            "AND i.title = :title AND i.status IN :statuses AND i.deletedAt IS NULL")
    boolean existsActiveWithTitle(TenantId tenantId, UUID vehicleId, String title, Collection<IssueStatus> statuses);
}