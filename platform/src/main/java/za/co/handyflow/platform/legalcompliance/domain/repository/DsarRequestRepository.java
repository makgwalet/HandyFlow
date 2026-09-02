package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequest;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DsarRequestRepository extends JpaRepository<DsarRequest, UUID> {

    @Query("""
        SELECT d FROM DsarRequest d WHERE d.tenantId = :#{#tenantId.value}
        AND d.deletedAt IS NULL
        AND (:status IS NULL OR d.status = :status)
        ORDER BY d.dueDate ASC
        """)
    Page<DsarRequest> findAllActive(TenantId tenantId, DsarStatus status, Pageable pageable);

    @Query("SELECT d FROM DsarRequest d WHERE d.tenantId = :#{#tenantId.value} AND d.id = :id AND d.deletedAt IS NULL")
    Optional<DsarRequest> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT d FROM DsarRequest d WHERE d.tenantId = :#{#tenantId.value}
        AND d.deletedAt IS NULL AND d.status IN ('RECEIVED','IN_PROGRESS')
        ORDER BY d.dueDate ASC
        """)
    List<DsarRequest> findOpen(TenantId tenantId);

    @Query("""
    SELECT d FROM DsarRequest d WHERE d.deletedAt IS NULL
    AND d.status IN ('RECEIVED','IN_PROGRESS')
    ORDER BY d.dueDate ASC
    """)
    List<DsarRequest> findOpenAcrossTenants();
}
