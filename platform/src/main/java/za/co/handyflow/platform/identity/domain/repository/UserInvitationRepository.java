package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.identity.domain.model.UserInvitation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

    List<UserInvitation> findByTenantIdOrderByCreatedAtDesc(TenantId tenantId);

    Optional<UserInvitation> findByToken(String token);

    boolean existsByTenantIdAndEmailAndStatus(
            TenantId tenantId, String email,
            UserInvitation.InvitationStatus status);
}
