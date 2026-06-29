// security/domain/repository/RotationPatternRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.RotationPattern;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RotationPatternRepository extends JpaRepository<RotationPattern, UUID> {

    @Query("""
        SELECT p FROM RotationPattern p
        WHERE p.tenantId = :tenantId
        AND p.active = true
        ORDER BY p.name
        """)
    Page<RotationPattern> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT p FROM RotationPattern p
        WHERE p.tenantId = :tenantId
        AND p.id = :id
        """)
    Optional<RotationPattern> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT p FROM RotationPattern p
        WHERE p.tenantId = :tenantId
        AND p.siteId = :siteId
        AND p.active = true
        ORDER BY p.name
        """)
    List<RotationPattern> findBySite(TenantId tenantId, UUID siteId);
}
