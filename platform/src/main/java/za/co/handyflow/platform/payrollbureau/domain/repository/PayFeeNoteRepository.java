package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayFeeNote;

import java.util.Optional;
import java.util.UUID;

public interface PayFeeNoteRepository extends JpaRepository<PayFeeNote, UUID> {

    @Query("SELECT f FROM PayFeeNote f WHERE f.payClientId = :clientId ORDER BY f.invoiceDate DESC")
    Page<PayFeeNote> findByClient(@Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT f FROM PayFeeNote f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<PayFeeNote> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Fee notes a client's portal users see: everything but cancelled ones. */
    @Query("SELECT f FROM PayFeeNote f WHERE f.payClientId = :clientId AND f.status <> 'CANCELLED' ORDER BY f.invoiceDate DESC")
    Page<PayFeeNote> findByClientForPortal(@Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT f FROM PayFeeNote f WHERE f.tenantId = :tenantId AND f.payRunId = :runId AND f.status <> 'CANCELLED'")
    java.util.List<PayFeeNote> findActiveByRun(@Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    @Query("SELECT f FROM PayFeeNote f WHERE f.tenantId = :tenantId AND f.status <> 'CANCELLED' AND f.invoiceDate >= :from AND f.invoiceDate <= :to")
    java.util.List<PayFeeNote> findBilledInRange(@Param("tenantId") UUID tenantId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    @Query("SELECT f.payRunId FROM PayFeeNote f WHERE f.tenantId = :tenantId AND f.status <> 'CANCELLED' AND f.payRunId IN :runIds")
    java.util.List<UUID> findBilledRunIds(@Param("tenantId") UUID tenantId, @Param("runIds") java.util.Collection<UUID> runIds);
}
