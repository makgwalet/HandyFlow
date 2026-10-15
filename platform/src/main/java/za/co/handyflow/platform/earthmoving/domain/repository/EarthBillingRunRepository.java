package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthBillingRun;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EarthBillingRunRepository extends JpaRepository<EarthBillingRun, UUID> {

    /** Newest first. */
    @Query("SELECT r FROM EarthBillingRun r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    List<EarthBillingRun> findRecent(TenantId tenantId, Pageable pageable);

    @Query("SELECT COUNT(r) > 0 FROM EarthBillingRun r WHERE r.tenantId = :tenantId AND r.periodMonth = :month AND r.triggerSource = 'SCHEDULED'")
    boolean scheduledRunExists(TenantId tenantId, LocalDate month);
}
