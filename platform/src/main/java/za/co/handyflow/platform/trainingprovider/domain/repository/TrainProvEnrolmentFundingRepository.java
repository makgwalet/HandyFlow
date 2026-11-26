package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvEnrolmentFunding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvEnrolmentFundingRepository extends JpaRepository<TrainProvEnrolmentFunding, UUID> {

    @Query("""
        SELECT l FROM TrainProvEnrolmentFunding l WHERE l.tenantId = :#{#tenantId.value}
        AND (CAST(:enrolmentId AS uuid) IS NULL OR l.enrolmentId = :enrolmentId)
        AND (CAST(:funderId AS uuid) IS NULL OR l.funderId = :funderId)
        AND (:status IS NULL OR l.status = :status)
        ORDER BY l.fundedFrom DESC, l.createdAt DESC
        """)
    List<TrainProvEnrolmentFunding> search(TenantId tenantId, UUID enrolmentId, UUID funderId, String status);

    @Query("SELECT l FROM TrainProvEnrolmentFunding l WHERE l.tenantId = :#{#tenantId.value} AND l.id = :id")
    Optional<TrainProvEnrolmentFunding> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(l) > 0 FROM TrainProvEnrolmentFunding l WHERE l.tenantId = :#{#tenantId.value} AND l.enrolmentId = :enrolmentId AND l.funderId = :funderId AND l.status = 'ACTIVE'")
    boolean liveExists(TenantId tenantId, UUID enrolmentId, UUID funderId);

    @Query("SELECT COUNT(l) FROM TrainProvEnrolmentFunding l WHERE l.tenantId = :#{#tenantId.value} AND l.funderId = :funderId AND l.status = 'ACTIVE'")
    long activeForFunder(TenantId tenantId, UUID funderId);
}
