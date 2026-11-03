package za.co.handyflow.platform.projects.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalRecipient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectTransmittalRecipientRepository extends JpaRepository<ProjectTransmittalRecipient, UUID> {

    @Query("""
            SELECT r FROM ProjectTransmittalRecipient r
            WHERE r.tenantId = :tenantId AND r.transmittalId = :transmittalId
            ORDER BY r.createdAt
            """)
    List<ProjectTransmittalRecipient> findByTransmittal(@Param("tenantId") UUID tenantId,
                                                        @Param("transmittalId") UUID transmittalId);

    @Query("""
            SELECT r FROM ProjectTransmittalRecipient r
            WHERE r.tenantId = :tenantId AND r.transmittalId = :transmittalId AND r.id = :id
            """)
    Optional<ProjectTransmittalRecipient> findByTransmittalAndId(@Param("tenantId") UUID tenantId,
                                                                 @Param("transmittalId") UUID transmittalId,
                                                                 @Param("id") UUID id);

    /** Locked: code checks and revoke/resend must not race (a wrong-guess counter must not be lost). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM ProjectTransmittalRecipient r
            WHERE r.tenantId = :tenantId AND r.transmittalId = :transmittalId AND r.id = :id
            """)
    Optional<ProjectTransmittalRecipient> findByTransmittalAndIdForUpdate(@Param("tenantId") UUID tenantId,
                                                                          @Param("transmittalId") UUID transmittalId,
                                                                          @Param("id") UUID id);

    @Query("SELECT r FROM ProjectTransmittalRecipient r WHERE r.tokenHash = :hash")
    Optional<ProjectTransmittalRecipient> findByTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ProjectTransmittalRecipient r WHERE r.tokenHash = :hash")
    Optional<ProjectTransmittalRecipient> findByTokenHashForUpdate(@Param("hash") String hash);
}
