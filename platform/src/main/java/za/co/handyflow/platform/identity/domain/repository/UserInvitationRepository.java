package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.identity.domain.model.UserInvitation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

    List<UserInvitation> findByTenantIdOrderByCreatedAtDesc(TenantId tenantId);

    Optional<UserInvitation> findByTokenHash(String tokenHash);

    /** Marks every pending invitation past its expiry as EXPIRED; returns the number changed. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update UserInvitation i set i.status = :expired where i.status = :pending and i.expiresAt < :now")
    int markExpired(@org.springframework.data.repository.query.Param("now") java.time.Instant now,
                    @org.springframework.data.repository.query.Param("pending") UserInvitation.InvitationStatus pending,
                    @org.springframework.data.repository.query.Param("expired") UserInvitation.InvitationStatus expired);

    boolean existsByTenantIdAndEmailAndStatus(
            TenantId tenantId, String email,
            UserInvitation.InvitationStatus status);
}
