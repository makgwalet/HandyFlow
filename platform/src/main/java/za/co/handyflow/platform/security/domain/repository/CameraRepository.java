// security/domain/repository/CameraRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Camera;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CameraRepository extends JpaRepository<Camera, UUID> {

    @Query("""
        SELECT c FROM Camera c
        WHERE c.tenantId = :tenantId
        AND c.id = :id
        """)
    Optional<Camera> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT c FROM Camera c
        WHERE c.tenantId = :tenantId
        AND c.siteId = :siteId
        AND c.status != 'DECOMMISSIONED'
        ORDER BY c.name
        """)
    List<Camera> findActiveBySite(TenantId tenantId, UUID siteId);
}
