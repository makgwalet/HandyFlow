// security/domain/repository/PatrolRouteRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.PatrolRoute;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrolRouteRepository extends JpaRepository<PatrolRoute, UUID> {

    @Query("""
        SELECT r FROM PatrolRoute r
        WHERE r.tenantId = :tenantId
        AND r.siteId = :siteId
        AND r.active = true
        ORDER BY r.name
        """)
    List<PatrolRoute> findActiveBySite(TenantId tenantId, UUID siteId);

    @Query("""
        SELECT r FROM PatrolRoute r
        WHERE r.tenantId = :tenantId
        AND r.id = :id
        """)
    Optional<PatrolRoute> findByTenantAndId(TenantId tenantId, UUID id);
}
