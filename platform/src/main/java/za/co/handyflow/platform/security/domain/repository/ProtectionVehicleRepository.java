// security/domain/repository/ProtectionVehicleRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.ProtectionVehicle;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ProtectionVehicleRepository extends JpaRepository<ProtectionVehicle, UUID> {

    @Query("""
        SELECT v FROM ProtectionVehicle v
        WHERE v.tenantId = :tenantId
        AND v.id = :id
        """)
    Optional<ProtectionVehicle> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT v FROM ProtectionVehicle v
        WHERE v.tenantId = :tenantId
        AND v.status != 'DECOMMISSIONED'
        ORDER BY v.registration
        """)
    Page<ProtectionVehicle> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT COUNT(v) > 0 FROM ProtectionVehicle v
        WHERE v.tenantId = :tenantId
        AND v.registration = :registration
        """)
    boolean existsByRegistration(TenantId tenantId, String registration);
}
