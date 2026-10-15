package za.co.handyflow.platform.auditor.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.auditor.domain.model.AuditQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditQueryRepository extends JpaRepository<AuditQuery, UUID> {
    List<AuditQuery> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<AuditQuery> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Highest number used so far for a reference prefix such as "Q-" or "RN-". */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(q.reference, :from) AS integer)), 0) FROM AuditQuery q WHERE q.tenantId = :tenantId AND q.kind = :kind")
    int highestNumber(@Param("tenantId") UUID tenantId, @Param("kind") String kind, @Param("from") int from);
}
