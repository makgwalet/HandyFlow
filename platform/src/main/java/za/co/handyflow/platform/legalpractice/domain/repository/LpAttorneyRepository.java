package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpAttorney;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped attorney/staff roster. No soft delete — {@code active} is a status flag. */
public interface LpAttorneyRepository extends JpaRepository<LpAttorney, UUID> {

    @Query("SELECT a FROM LpAttorney a WHERE a.tenantId = :tenantId ORDER BY a.name ASC")
    Page<LpAttorney> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT a FROM LpAttorney a WHERE a.tenantId = :tenantId AND a.active = true ORDER BY a.name ASC")
    List<LpAttorney> findAllActiveList(TenantId tenantId);

    @Query("SELECT a FROM LpAttorney a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<LpAttorney> findActiveById(TenantId tenantId, UUID id);
}
