// security/domain/repository/CpEvidenceRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.CpEvidence;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CpEvidenceRepository extends JpaRepository<CpEvidence, UUID> {

    @Query("""
        SELECT e FROM CpEvidence e
        WHERE e.tenantId = :tenantId
        AND e.id = :id
        AND e.deletedAt IS NULL
        """)
    Optional<CpEvidence> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT e FROM CpEvidence e
        WHERE e.tenantId = :tenantId
        AND e.entityType = :entityType
        AND e.entityId = :entityId
        AND e.deletedAt IS NULL
        ORDER BY e.createdAt DESC
        """)
    List<CpEvidence> findActiveForEntity(TenantId tenantId, CpEvidence.EntityType entityType, UUID entityId);
}