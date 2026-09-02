package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryObligationRepository extends JpaRepository<RegulatoryObligation, UUID> {

    @Query("""
        SELECT o FROM RegulatoryObligation o WHERE o.tenantId = :#{#tenantId.value}
        AND o.deletedAt IS NULL
        AND (:status IS NULL OR o.status = :status)
        ORDER BY o.reviewDate ASC
        """)
    Page<RegulatoryObligation> findAllActive(TenantId tenantId, ObligationStatus status, Pageable pageable);

    @Query("SELECT o FROM RegulatoryObligation o WHERE o.tenantId = :#{#tenantId.value} AND o.id = :id AND o.deletedAt IS NULL")
    Optional<RegulatoryObligation> findActiveById(TenantId tenantId, UUID id);

    /** Cross-tenant — used only by the daily status-refresh/notification scheduler, same shape as FleetNotificationScheduler/PsiraComplianceScheduler's own cross-tenant sweeps. */
    @Query("SELECT o FROM RegulatoryObligation o WHERE o.deletedAt IS NULL AND o.status <> 'NON_COMPLIANT'")
    List<RegulatoryObligation> findAllActiveAcrossTenants();

    @Query("""
        SELECT o FROM RegulatoryObligation o WHERE o.tenantId = :#{#tenantId.value}
        AND o.deletedAt IS NULL AND o.reviewDate BETWEEN :from AND :to
        ORDER BY o.reviewDate ASC
        """)
    List<RegulatoryObligation> findDueWithin(TenantId tenantId, LocalDate from, LocalDate to);
}
