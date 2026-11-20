package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpDeadlineComputation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpDeadlineComputationRepository extends JpaRepository<LpDeadlineComputation, UUID> {

    @Query("SELECT c FROM LpDeadlineComputation c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<LpDeadlineComputation> findActiveById(TenantId tenantId, UUID id);

    /** Every computation on a matter, newest first. */
    @Query("SELECT c FROM LpDeadlineComputation c WHERE c.tenantId = :tenantId AND c.matterId = :matterId ORDER BY c.computedAt DESC")
    List<LpDeadlineComputation> findAllForMatter(TenantId tenantId, UUID matterId);

    /** Is this computation already superseded? The database also allows only one successor. */
    @Query("SELECT count(c) FROM LpDeadlineComputation c WHERE c.tenantId = :tenantId AND c.supersedesId = :id")
    long countSuccessors(TenantId tenantId, UUID id);
}
