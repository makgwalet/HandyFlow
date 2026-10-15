package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthChecklistVersion;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthChecklistVersionRepository extends JpaRepository<EarthChecklistVersion, UUID> {

    @Query("SELECT v FROM EarthChecklistVersion v WHERE v.tenantId = :tenantId AND v.id = :id")
    Optional<EarthChecklistVersion> findOwned(TenantId tenantId, UUID id);

    @Query("SELECT v FROM EarthChecklistVersion v WHERE v.tenantId = :tenantId AND v.inspectionType = :type ORDER BY v.versionNo DESC")
    List<EarthChecklistVersion> findByType(TenantId tenantId, String type);

    @Query("SELECT v FROM EarthChecklistVersion v WHERE v.tenantId = :tenantId AND v.inspectionType = :type AND v.status = 'PUBLISHED' AND v.effectiveFrom <= :today ORDER BY v.versionNo DESC")
    List<EarthChecklistVersion> findInForce(TenantId tenantId, String type, LocalDate today, Pageable page);

    @Query("SELECT v FROM EarthChecklistVersion v WHERE v.tenantId = :tenantId AND v.inspectionType = :type AND v.status = 'DRAFT'")
    Optional<EarthChecklistVersion> findDraft(TenantId tenantId, String type);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM EarthChecklistVersion v WHERE v.tenantId = :tenantId AND v.inspectionType = :type")
    int latestNumber(TenantId tenantId, String type);
}
