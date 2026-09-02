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

    // FIX: RegulatoryObligation.tenantId is inherited from AggregateRoot as
    // @Embedded TenantId (not a raw UUID column) — so o.tenantId is itself
    // of type TenantId. The previous ":#{#tenantId.value}" SpEL expression
    // unwrapped the tenantId parameter down to a raw UUID scalar before
    // binding, which only makes sense when the entity's own column is a
    // raw UUID (e.g. TenantModule.tenantId). Comparing an embedded
    // TenantId path against a raw UUID parameter is a genuine type
    // mismatch — confirmed at runtime: Hibernate 6.6.13 rejects it with
    // "Argument [<uuid>] of type [UUID] did not match parameter type
    // [TenantId]". Binding the TenantId parameter directly (no unwrap) is
    // the correct form here, matching the working convention used
    // elsewhere for AggregateRoot-based entities (see LpMatterRepository,
    // AccessPointRepository). This bug was never caught before because
    // every legalcompliance endpoint was already being rejected earlier by
    // FeatureGuard's module-activation check (see EntitlementService fix)
    // — these queries were never actually reached until that was fixed.
    @Query("""
        SELECT o FROM RegulatoryObligation o WHERE o.tenantId = :tenantId
        AND o.deletedAt IS NULL
        AND (:status IS NULL OR o.status = :status)
        ORDER BY o.reviewDate ASC
        """)
    Page<RegulatoryObligation> findAllActive(TenantId tenantId, ObligationStatus status, Pageable pageable);

    @Query("SELECT o FROM RegulatoryObligation o WHERE o.tenantId = :tenantId AND o.id = :id AND o.deletedAt IS NULL")
    Optional<RegulatoryObligation> findActiveById(TenantId tenantId, UUID id);

    /** Cross-tenant — used only by the daily status-refresh/notification scheduler, same shape as FleetNotificationScheduler/PsiraComplianceScheduler's own cross-tenant sweeps. */
    @Query("SELECT o FROM RegulatoryObligation o WHERE o.deletedAt IS NULL AND o.status <> 'NON_COMPLIANT'")
    List<RegulatoryObligation> findAllActiveAcrossTenants();

    @Query("""
        SELECT o FROM RegulatoryObligation o WHERE o.tenantId = :tenantId
        AND o.deletedAt IS NULL AND o.reviewDate BETWEEN :from AND :to
        ORDER BY o.reviewDate ASC
        """)
    List<RegulatoryObligation> findDueWithin(TenantId tenantId, LocalDate from, LocalDate to);
}