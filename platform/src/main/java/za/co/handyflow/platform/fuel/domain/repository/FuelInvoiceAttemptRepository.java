package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import za.co.handyflow.platform.fuel.domain.model.FuelInvoiceAttempt;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelInvoiceAttemptRepository extends JpaRepository<FuelInvoiceAttempt, UUID> {

    @Query("SELECT a FROM FuelInvoiceAttempt a WHERE a.tenantId = :tenantId ORDER BY a.startedAt DESC")
    List<FuelInvoiceAttempt> findRecent(TenantId tenantId, Pageable pageable);

    /** Open problems: invoice created but unlinked, or an attempt that never finished. Newest first. */
    @Query("SELECT a FROM FuelInvoiceAttempt a WHERE a.tenantId = :tenantId AND a.status IN ('LINK_FAILED','STARTED') ORDER BY a.startedAt DESC")
    List<FuelInvoiceAttempt> findOpen(TenantId tenantId, Pageable pageable);

    /** Half-finished attempts (invoice exists, deliveries not linked) that cover any of these deliveries. */
    @Query("SELECT DISTINCT a FROM FuelInvoiceAttempt a JOIN a.deliveryIds d WHERE a.tenantId = :tenantId AND a.status = 'LINK_FAILED' AND d IN :ids")
    List<FuelInvoiceAttempt> findUnlinkedCovering(TenantId tenantId, java.util.Collection<UUID> ids);

    @Query("SELECT a FROM FuelInvoiceAttempt a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<FuelInvoiceAttempt> findOwned(TenantId tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FuelInvoiceAttempt a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<FuelInvoiceAttempt> findOwnedForUpdate(TenantId tenantId, UUID id);
}
