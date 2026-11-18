package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelArrivalCheckRelease;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelArrivalCheckReleaseRepository extends JpaRepository<FuelArrivalCheckRelease, UUID> {

    /** Newest first. Ask for a page of one to get the latest release of a check. */
    @Query("SELECT r FROM FuelArrivalCheckRelease r WHERE r.tenantId = :tenantId AND r.checkId = :checkId ORDER BY r.releasedAt DESC")
    List<FuelArrivalCheckRelease> forCheck(TenantId tenantId, UUID checkId, Pageable page);
}
