package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopTabRepository extends JpaRepository<FuelTruckStopTab, UUID> {

    @Query("SELECT t FROM FuelTruckStopTab t WHERE t.tenantId = :tenantId AND (:status IS NULL OR t.status = :status) ORDER BY t.openedAt DESC")
    List<FuelTruckStopTab> findRecent(TenantId tenantId, String status, Pageable page);

    @Query("SELECT t FROM FuelTruckStopTab t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<FuelTruckStopTab> findOwned(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(t) FROM FuelTruckStopTab t WHERE t.tenantId = :tenantId AND t.status = 'OPEN'")
    long countOpen(TenantId tenantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopTab t SET t.invoiceState = 'PENDING' WHERE t.tenantId = :tenantId AND t.id = :id AND t.status = 'SETTLED' AND t.accountId IS NOT NULL AND t.invoiceState IS NULL")
    int markPending(TenantId tenantId, UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopTab t SET t.invoiceState = 'CLAIMED', t.invoiceClaimedAt = :now, t.invoiceError = NULL WHERE t.tenantId = :tenantId AND t.id = :id AND t.status = 'SETTLED' AND t.accountId IS NOT NULL "
            + "AND (t.invoiceState IN ('PENDING','FAILED') OR (t.invoiceState = 'CLAIMED' AND t.invoiceClaimedAt < :stale))")
    int claimForInvoicing(TenantId tenantId, UUID id, Instant now, Instant stale);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopTab t SET t.invoiceState = 'INVOICED', t.invoiceId = :invoiceId, t.invoiceNumber = :number, t.invoiceError = NULL WHERE t.tenantId = :tenantId AND t.id = :id")
    int markInvoiced(TenantId tenantId, UUID id, UUID invoiceId, String number);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopTab t SET t.invoiceState = 'FAILED', t.invoiceError = :message WHERE t.tenantId = :tenantId AND t.id = :id AND t.invoiceState = 'CLAIMED'")
    int markFailed(TenantId tenantId, UUID id, String message);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopTab t SET t.invoiceState = 'LINK_FAILED', t.invoiceId = :invoiceId, t.invoiceNumber = :number, t.invoiceError = :message WHERE t.tenantId = :tenantId AND t.id = :id")
    int markLinkFailed(TenantId tenantId, UUID id, UUID invoiceId, String number, String message);
}
