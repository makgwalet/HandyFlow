// security/domain/repository/SecurityDeviceRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.SecurityDevice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityDeviceRepository extends JpaRepository<SecurityDevice, UUID> {

    @Query("""
        SELECT d FROM SecurityDevice d
        WHERE d.tenantId = :tenantId
        AND d.deviceHardwareId = :deviceHardwareId
        """)
    Optional<SecurityDevice> findByTenantIdAndDeviceHardwareId(
            TenantId tenantId, String deviceHardwareId);

    @Query("""
        SELECT d FROM SecurityDevice d
        WHERE d.tenantId = :tenantId
        AND d.siteId = :siteId
        AND d.status = 'ACTIVE'
        """)
    List<SecurityDevice> findActiveBySite(TenantId tenantId, UUID siteId);

    @Query("""
        SELECT d FROM SecurityDevice d
        WHERE d.tenantId = :tenantId
        ORDER BY d.deviceName
        """)
    List<SecurityDevice> findAllByTenant(TenantId tenantId);
}
