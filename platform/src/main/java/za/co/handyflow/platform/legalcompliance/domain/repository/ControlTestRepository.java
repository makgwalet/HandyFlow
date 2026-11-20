package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ControlTestRepository extends JpaRepository<ControlTest, UUID> {

    @Query("SELECT t FROM ControlTest t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<ControlTest> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT t FROM ControlTest t WHERE t.tenantId = :tenantId
        AND (:status IS NULL OR t.status = :status)
        AND (:controlId IS NULL OR t.controlId = :controlId)
        ORDER BY t.plannedDate DESC, t.reference DESC
        """)
    List<ControlTest> search(TenantId tenantId, ControlTestStatus status, UUID controlId, Pageable pageable);

    /** Everything, for summaries, coverage and sweeps that must count every row. */
    default List<ControlTest> search(TenantId tenantId, ControlTestStatus status, UUID controlId) { return search(tenantId, status, controlId, Pageable.unpaged()); }

    /** In the query's own order, at most RowLimit.MAX_ROWS: for list screens only. */
    default List<ControlTest> searchCapped(TenantId tenantId, ControlTestStatus status, UUID controlId) { return search(tenantId, status, controlId, RowLimit.first()); }

    @Query("SELECT t FROM ControlTest t WHERE t.tenantId = :tenantId AND t.controlId = :controlId AND t.status IN (za.co.handyflow.platform.legalcompliance.domain.model.ControlTestStatus.PLANNED, za.co.handyflow.platform.legalcompliance.domain.model.ControlTestStatus.IN_PROGRESS, za.co.handyflow.platform.legalcompliance.domain.model.ControlTestStatus.SUBMITTED)")
    Optional<ControlTest> findOpenForControl(TenantId tenantId, UUID controlId);

    @Query("SELECT t FROM ControlTest t WHERE t.tenantId = :tenantId AND t.controlId = :controlId AND t.status = za.co.handyflow.platform.legalcompliance.domain.model.ControlTestStatus.COMPLETED ORDER BY t.reviewedAt DESC")
    List<ControlTest> findCompletedForControl(TenantId tenantId, UUID controlId);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
