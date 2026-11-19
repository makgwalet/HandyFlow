package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
    List<ControlTest> search(TenantId tenantId, ControlTestStatus status, UUID controlId);

    @Query("SELECT t FROM ControlTest t WHERE t.tenantId = :tenantId AND t.controlId = :controlId AND t.status IN (za.co.handyflow.platform.legalcompliance.domain.model.ControlTestStatus.PLANNED, za.co.handyflow.platform.legalcompliance.domain.model.ControlTestStatus.IN_PROGRESS, za.co.handyflow.platform.legalcompliance.domain.model.ControlTestStatus.SUBMITTED)")
    Optional<ControlTest> findOpenForControl(TenantId tenantId, UUID controlId);

    @Query("SELECT t FROM ControlTest t WHERE t.tenantId = :tenantId AND t.controlId = :controlId AND t.status = za.co.handyflow.platform.legalcompliance.domain.model.ControlTestStatus.COMPLETED ORDER BY t.reviewedAt DESC")
    List<ControlTest> findCompletedForControl(TenantId tenantId, UUID controlId);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
