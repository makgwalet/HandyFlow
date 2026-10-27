package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilitySyncOp;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

public interface FacilitySyncOpRepository extends JpaRepository<FacilitySyncOp, UUID> {

    @Query("SELECT COUNT(o) > 0 FROM FacilitySyncOp o WHERE o.tenantId.value = :#{#tenantId.value} AND o.userId = :userId AND o.opId = :opId")
    boolean exists(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId, @Param("opId") UUID opId);

    /** Retention: operations older than the longest a device could plausibly stay offline. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM FacilitySyncOp o WHERE o.appliedAt < :before")
    int deleteAppliedBefore(@Param("before") Instant before);
}
