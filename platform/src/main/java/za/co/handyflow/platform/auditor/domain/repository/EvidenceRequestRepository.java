package za.co.handyflow.platform.auditor.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.auditor.domain.model.EvidenceRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRequestRepository extends JpaRepository<EvidenceRequest, UUID> {
    List<EvidenceRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<EvidenceRequest> findByTenantIdAndId(UUID tenantId, UUID id);
    long countByTenantId(UUID tenantId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(r.reference, 4) AS integer)), 0) FROM EvidenceRequest r WHERE r.tenantId = :tenantId")
    int highestNumber(@Param("tenantId") UUID tenantId);
}
