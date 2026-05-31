package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.identity.domain.model.PasswordResetToken;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    /** Invalidate all unused tokens for a user before issuing a new one. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.userId = :userId AND t.used = false")
    void invalidateAllForUser(UUID userId);
}
