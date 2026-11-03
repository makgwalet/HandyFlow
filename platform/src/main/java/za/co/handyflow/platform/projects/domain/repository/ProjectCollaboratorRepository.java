package za.co.handyflow.platform.projects.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectCollaborator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCollaboratorRepository extends JpaRepository<ProjectCollaborator, UUID> {

    @Query("SELECT c FROM ProjectCollaborator c WHERE c.tenantId = :tenantId ORDER BY c.name")
    List<ProjectCollaborator> findByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM ProjectCollaborator c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<ProjectCollaborator> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ProjectCollaborator c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<ProjectCollaborator> findByTenantAndIdForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Any account for this address, enabled or not (to refuse a duplicate registration). */
    @Query("SELECT c FROM ProjectCollaborator c WHERE c.tenantId = :tenantId AND LOWER(c.email) = LOWER(:email)")
    Optional<ProjectCollaborator> findByEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    /** The enabled account for this address, used to link new transmittal recipients to it. */
    @Query("SELECT c FROM ProjectCollaborator c WHERE c.tenantId = :tenantId AND LOWER(c.email) = LOWER(:email) AND c.disabledAt IS NULL")
    Optional<ProjectCollaborator> findActiveByEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    @Query("SELECT c FROM ProjectCollaborator c WHERE c.tokenHash = :hash")
    Optional<ProjectCollaborator> findByTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ProjectCollaborator c WHERE c.tokenHash = :hash")
    Optional<ProjectCollaborator> findByTokenHashForUpdate(@Param("hash") String hash);
}
