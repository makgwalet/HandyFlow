package za.co.handyflow.platform.secureaccess.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.secureaccess.domain.model.SecureRecipient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecureRecipientRepository extends JpaRepository<SecureRecipient, UUID> {

    @Query("""
            SELECT r FROM SecureRecipient r
            WHERE r.tenantId = :tenantId AND r.shareId = :shareId
            ORDER BY r.createdAt
            """)
    List<SecureRecipient> findByShare(@Param("tenantId") UUID tenantId,
                                                        @Param("shareId") UUID shareId);

    @Query("""
            SELECT r FROM SecureRecipient r
            WHERE r.tenantId = :tenantId AND r.shareId = :shareId AND r.id = :id
            """)
    Optional<SecureRecipient> findByShareAndId(@Param("tenantId") UUID tenantId,
                                                                 @Param("shareId") UUID shareId,
                                                                 @Param("id") UUID id);

    /** Locked: code checks and revoke/resend must not race (a wrong-guess counter must not be lost). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM SecureRecipient r
            WHERE r.tenantId = :tenantId AND r.shareId = :shareId AND r.id = :id
            """)
    Optional<SecureRecipient> findByShareAndIdForUpdate(@Param("tenantId") UUID tenantId,
                                                                          @Param("shareId") UUID shareId,
                                                                          @Param("id") UUID id);

    @Query("SELECT r FROM SecureRecipient r WHERE r.tokenHash = :hash")
    Optional<SecureRecipient> findByTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SecureRecipient r WHERE r.tokenHash = :hash")
    Optional<SecureRecipient> findByTokenHashForUpdate(@Param("hash") String hash);

    /** Every recipient row issued to a registered collaborator, newest first (their workspace). */
    @Query("""
            SELECT r FROM SecureRecipient r
            WHERE r.tenantId = :tenantId AND r.collaboratorId = :collaboratorId
            ORDER BY r.createdAt DESC
            """)
    List<SecureRecipient> findByCollaborator(@Param("tenantId") UUID tenantId,
                                                         @Param("collaboratorId") UUID collaboratorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM SecureRecipient r
            WHERE r.tenantId = :tenantId AND r.collaboratorId = :collaboratorId AND r.id = :id
            """)
    Optional<SecureRecipient> findByCollaboratorAndIdForUpdate(@Param("tenantId") UUID tenantId,
                                                                           @Param("collaboratorId") UUID collaboratorId,
                                                                           @Param("id") UUID id);

    /** On registration: links the address's existing, not-revoked recipient rows to the new account. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SecureRecipient r SET r.collaboratorId = :collaboratorId
            WHERE r.tenantId = :tenantId AND LOWER(r.email) = LOWER(:email)
              AND r.revokedAt IS NULL AND r.collaboratorId IS NULL
            """)
    int linkExisting(@Param("tenantId") UUID tenantId, @Param("email") String email,
                     @Param("collaboratorId") UUID collaboratorId);
}
