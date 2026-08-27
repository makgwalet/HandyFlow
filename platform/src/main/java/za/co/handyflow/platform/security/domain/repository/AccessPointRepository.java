// security/domain/repository/AccessPointRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.AccessPoint;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** @Query JPQL throughout — same style DeviceSessionRepository already uses for embedded-TenantId queries in this exact module, confirmed working. */
public interface AccessPointRepository extends JpaRepository<AccessPoint, UUID> {

    @Query("SELECT a FROM AccessPoint a WHERE a.id = :id AND a.tenantId = :tenantId")
    Optional<AccessPoint> findByIdAndTenant(UUID id, TenantId tenantId);

    @Query("SELECT a FROM AccessPoint a WHERE a.tenantId = :tenantId AND a.siteId = :siteId AND a.active = true ORDER BY a.name")
    List<AccessPoint> findActiveBySite(TenantId tenantId, UUID siteId);
}