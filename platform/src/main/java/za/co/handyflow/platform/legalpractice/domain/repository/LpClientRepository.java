package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpClient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped client portfolio. No soft delete on {@link LpClient} —
 * {@code deactivate()}/{@code reactivate()} toggle {@code status} instead,
 * so every query here is a plain tenant scope, not an active-row filter.
 */
public interface LpClientRepository extends JpaRepository<LpClient, UUID> {

    @Query("SELECT c FROM LpClient c WHERE c.tenantId = :tenantId ORDER BY c.name ASC")
    Page<LpClient> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT c FROM LpClient c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<LpClient> findActiveById(TenantId tenantId, UUID id);
}
