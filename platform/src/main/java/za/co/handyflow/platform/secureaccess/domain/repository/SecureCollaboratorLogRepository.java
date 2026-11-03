package za.co.handyflow.platform.secureaccess.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.secureaccess.domain.model.SecureCollaboratorLog;

import java.util.List;
import java.util.UUID;

public interface SecureCollaboratorLogRepository extends JpaRepository<SecureCollaboratorLog, UUID> {

    @Query("""
            SELECT l FROM SecureCollaboratorLog l
            WHERE l.tenantId = :tenantId AND l.collaboratorId = :collaboratorId
            ORDER BY l.occurredAt DESC
            """)
    List<SecureCollaboratorLog> findByCollaborator(@Param("tenantId") UUID tenantId,
                                                    @Param("collaboratorId") UUID collaboratorId);
}
