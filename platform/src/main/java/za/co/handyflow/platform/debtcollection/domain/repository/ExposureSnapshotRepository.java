package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.ExposureSnapshot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExposureSnapshotRepository extends JpaRepository<ExposureSnapshot, UUID> {

    @Query("SELECT s FROM ExposureSnapshot s WHERE s.tenantId = :tenantId AND s.snapshotDate = :date")
    Optional<ExposureSnapshot> findByDate(TenantId tenantId, LocalDate date);

    @Query("SELECT s FROM ExposureSnapshot s WHERE s.tenantId = :tenantId AND s.snapshotDate BETWEEN :from AND :to ORDER BY s.snapshotDate")
    List<ExposureSnapshot> findBetween(TenantId tenantId, LocalDate from, LocalDate to);

    /** Cross-tenant: every tenant that currently holds at least one non-terminal case — the set the daily snapshot covers. */
    @Query("""
        SELECT DISTINCT c.tenantId FROM DebtCollectionCase c
        WHERE c.deletedAt IS NULL AND c.status NOT IN ('CLOSED','WRITTEN_OFF','SETTLED')
        """)
    List<TenantId> findTenantsWithActiveCases();
}
