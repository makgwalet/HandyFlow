package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.Finding;
import za.co.handyflow.platform.legalcompliance.domain.model.FindingStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    @Query("SELECT f FROM Finding f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<Finding> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT f FROM Finding f WHERE f.tenantId = :tenantId AND f.sourceType = :sourceType AND f.sourceId = :sourceId")
    Optional<Finding> findBySource(TenantId tenantId, String sourceType, UUID sourceId);

    @Query("""
        SELECT f FROM Finding f WHERE f.tenantId = :tenantId
        AND (:status IS NULL OR f.status = :status)
        AND (:controlId IS NULL OR f.controlId = :controlId)
        AND (:includeClosed = TRUE OR f.status <> za.co.handyflow.platform.legalcompliance.domain.model.FindingStatus.CLOSED)
        ORDER BY f.dueDate, f.reference
        """)
    List<Finding> search(TenantId tenantId, FindingStatus status, UUID controlId, boolean includeClosed);

    @Query("SELECT f FROM Finding f WHERE f.tenantId = :tenantId AND f.controlId = :controlId AND f.status <> za.co.handyflow.platform.legalcompliance.domain.model.FindingStatus.CLOSED")
    List<Finding> findOpenForControl(TenantId tenantId, UUID controlId);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();

    /** Cross-tenant — daily sweep. */
    @Query("SELECT f FROM Finding f WHERE f.status <> za.co.handyflow.platform.legalcompliance.domain.model.FindingStatus.CLOSED AND f.dueDate < :today")
    List<Finding> findOverdueAcrossTenants(LocalDate today);
}
