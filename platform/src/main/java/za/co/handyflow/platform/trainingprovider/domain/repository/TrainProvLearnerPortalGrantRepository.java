package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerPortalGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnerPortalGrantRepository extends JpaRepository<TrainProvLearnerPortalGrant, UUID> {

    @Query("SELECT g FROM TrainProvLearnerPortalGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.learnerId = :learnerId ORDER BY g.createdAt DESC")
    List<TrainProvLearnerPortalGrant> forLearner(TenantId tenantId, UUID learnerId);

    @Query("SELECT g FROM TrainProvLearnerPortalGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.id = :id")
    Optional<TrainProvLearnerPortalGrant> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(g) > 0 FROM TrainProvLearnerPortalGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.learnerId = :learnerId AND g.status IN ('PENDING', 'ACCEPTED')")
    boolean liveExists(TenantId tenantId, UUID learnerId);

    Optional<TrainProvLearnerPortalGrant> findByInviteToken(String inviteToken);

    @Query("SELECT g FROM TrainProvLearnerPortalGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACCEPTED'")
    List<TrainProvLearnerPortalGrant> acceptedForUser(UUID portalUserId);
}
