package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.identity.domain.model.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT r FROM RefreshToken r WHERE r.userId = :userId AND r.revokedAt IS NULL ORDER BY r.createdAt DESC")
    List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId);

    // Backs both "sign out everywhere" (explicit user action) and the
    // theft-response sweep (reuse of an already-rotated token detected —
    // see RefreshTokenService.refresh()). Single bulk statement rather
    // than loading every row into Java just to call revoke() on each —
    // this can legitimately be a lot of rows for a user with many
    // devices, and the security response needs to be immediate, not
    // waiting on N individual entity saves.
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM RefreshToken r WHERE r.userId = :userId AND r.revokedAt IS NULL AND r.expiresAt > :now")
    long countActiveSessionsForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}