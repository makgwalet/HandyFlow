package za.co.handyflow.platform.projects.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ChangeOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChangeOrderRepository extends JpaRepository<ChangeOrder, UUID> {

    @Query("""
            SELECT co FROM ChangeOrder co
            WHERE co.projectId = :projectId
            ORDER BY co.createdAt DESC
            """)
    List<ChangeOrder> findByProject(@Param("projectId") UUID projectId);

    @Query("""
            SELECT co FROM ChangeOrder co
            WHERE co.tenantId = :tenantId
              AND co.status   = 'SUBMITTED'
            ORDER BY co.submittedAt
            """)
    List<ChangeOrder> findPendingApproval(@Param("tenantId") UUID tenantId);

    @Query("SELECT co FROM ChangeOrder co WHERE co.tenantId = :tenantId AND co.id = :id")
    Optional<ChangeOrder> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                            @Param("id")       UUID id);

    /**
     * Same row, locked until the transaction ends. Approve, reject, withdraw and submit use it so two
     * people acting at once are serialised: without it both could pass the status check on stale data
     * and the approved change would be applied to the budget twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT co FROM ChangeOrder co WHERE co.tenantId = :tenantId AND co.id = :id")
    Optional<ChangeOrder> findByTenantAndIdForUpdate(@Param("tenantId") UUID tenantId,
                                                     @Param("id")       UUID id);
}
