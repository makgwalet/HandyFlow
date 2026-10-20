package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.AccidentEvidence;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccidentEvidenceRepository extends JpaRepository<AccidentEvidence, UUID> {

    @Query("SELECT e FROM AccidentEvidence e WHERE e.tenantId = :tenantId AND e.accidentId = :accidentId " +
            "AND e.deletedAt IS NULL ORDER BY e.uploadedAt")
    List<AccidentEvidence> findActiveByAccident(TenantId tenantId, UUID accidentId);

    @Query("SELECT e FROM AccidentEvidence e WHERE e.tenantId = :tenantId AND e.accidentId = :accidentId " +
            "AND e.id = :id AND e.deletedAt IS NULL")
    Optional<AccidentEvidence> findActiveById(TenantId tenantId, UUID accidentId, UUID id);

    @Query("SELECT COUNT(e) FROM AccidentEvidence e WHERE e.tenantId = :tenantId AND e.accidentId = :accidentId " +
            "AND e.deletedAt IS NULL")
    long countActiveByAccident(TenantId tenantId, UUID accidentId);
}
