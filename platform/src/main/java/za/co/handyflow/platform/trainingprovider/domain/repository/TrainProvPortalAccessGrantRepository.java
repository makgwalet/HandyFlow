package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvPortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvPortalAccessGrantRepository extends JpaRepository<TrainProvPortalAccessGrant, UUID> {

    Optional<TrainProvPortalAccessGrant> findByInviteToken(String inviteToken);

    @Query("SELECT g FROM TrainProvPortalAccessGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.clientId = :clientId ORDER BY g.createdAt DESC")
    List<TrainProvPortalAccessGrant> findAllForClient(TenantId tenantId, UUID clientId);

    @Query("SELECT g FROM TrainProvPortalAccessGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.id = :id")
    Optional<TrainProvPortalAccessGrant> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT g FROM TrainProvPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACCEPTED'")
    List<TrainProvPortalAccessGrant> findAcceptedByPortalUser(UUID portalUserId);
}
