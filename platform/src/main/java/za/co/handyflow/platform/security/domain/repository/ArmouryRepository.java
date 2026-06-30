// security/domain/repository/ArmouryRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Armoury;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArmouryRepository extends JpaRepository<Armoury, UUID> {

    @Query("""
        SELECT a FROM Armoury a
        WHERE a.tenantId = :tenantId
        AND a.id = :id
        """)
    Optional<Armoury> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT a FROM Armoury a
        WHERE a.tenantId = :tenantId
        AND a.status != 'DECOMMISSIONED'
        ORDER BY a.firearmSerial
        """)
    Page<Armoury> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT a FROM Armoury a
        WHERE a.tenantId = :tenantId
        AND a.assignedGuardId = :guardId
        AND a.status = 'ISSUED'
        """)
    List<Armoury> findIssuedToGuard(TenantId tenantId, UUID guardId);

    /** Firearms whose SAPS license expires within the warning window — drives the alert scheduler. */
    @Query("""
        SELECT a FROM Armoury a
        WHERE a.tenantId = :tenantId
        AND a.status != 'DECOMMISSIONED'
        AND a.licenseExpiry <= :warnDate
        ORDER BY a.licenseExpiry
        """)
    List<Armoury> findLicenseExpiringBy(TenantId tenantId, LocalDate warnDate);

    @Query("""
        SELECT COUNT(a) > 0 FROM Armoury a
        WHERE a.tenantId = :tenantId
        AND a.firearmSerial = :serial
        """)
    boolean existsBySerial(TenantId tenantId, String serial);
}
