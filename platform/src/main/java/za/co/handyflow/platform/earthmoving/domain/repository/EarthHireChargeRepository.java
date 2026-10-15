package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthHireCharge;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthHireChargeRepository extends JpaRepository<EarthHireCharge, UUID> {

    @Query("SELECT c FROM EarthHireCharge c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<EarthHireCharge> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT c FROM EarthHireCharge c WHERE c.tenantId = :tenantId AND c.id IN :ids")
    List<EarthHireCharge> findByTenantAndIds(TenantId tenantId, Collection<UUID> ids);

    /** Newest first; a null status means all. */
    @Query("SELECT c FROM EarthHireCharge c WHERE c.tenantId = :tenantId AND (:status IS NULL OR c.status = :status) ORDER BY c.createdAt DESC")
    List<EarthHireCharge> findForTenant(TenantId tenantId, String status);

    /** Live (not voided) charges of this deployment that overlap the period. */
    /** Charges that count as revenue (not voided) whose period starts inside the dates, both ends included. */
    @Query("SELECT c FROM EarthHireCharge c WHERE c.tenantId = :tenantId AND c.status <> 'VOIDED' AND c.periodFrom >= :from AND c.periodFrom <= :to")
    List<EarthHireCharge> findRevenueBetween(TenantId tenantId, LocalDate from, LocalDate to);

    @Query("SELECT c FROM EarthHireCharge c WHERE c.tenantId = :tenantId AND c.deploymentId = :deploymentId AND c.status <> 'VOIDED' " +
            "AND c.periodFrom <= :to AND c.periodTo >= :from")
    List<EarthHireCharge> findLiveOverlapping(TenantId tenantId, UUID deploymentId, LocalDate from, LocalDate to);
}
