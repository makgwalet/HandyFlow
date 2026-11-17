package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopStayRepository extends JpaRepository<FuelTruckStopStay, UUID> {

    @Query("SELECT s FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId ORDER BY s.arrivalDate DESC, s.createdAt DESC")
    List<FuelTruckStopStay> findRecent(TenantId tenantId, Pageable page);

    @Query("SELECT s FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId AND s.accountId = :accountId ORDER BY s.arrivalDate DESC, s.createdAt DESC")
    List<FuelTruckStopStay> findForAccount(TenantId tenantId, UUID accountId, Pageable page);

    @Query("SELECT COUNT(s) FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId AND s.status IN ('BOOKED','CHECKED_IN')")
    long countOpen(TenantId tenantId);

    @Query("SELECT s FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<FuelTruckStopStay> findOwned(TenantId tenantId, UUID id);

    /** Stays holding a bay (booked or in the yard) whose dates touch [from, to). In-yard stays are included even when planned to have left, so overstays count. */
    @Query("SELECT s FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId AND s.status IN ('BOOKED','CHECKED_IN') AND s.arrivalDate < :to AND (s.departureDate > :from OR s.status = 'CHECKED_IN')")
    List<FuelTruckStopStay> findHoldingBays(TenantId tenantId, LocalDate from, LocalDate to);

    /** A checked-out stay on an account that has not been invoiced yet is queued for invoicing. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopStay s SET s.invoiceState = 'PENDING' WHERE s.tenantId = :tenantId AND s.id = :id AND s.status = 'CHECKED_OUT' AND s.accountId IS NOT NULL AND s.invoiceState IS NULL")
    int markPending(TenantId tenantId, UUID id);

    /** Takes the stay for invoicing. Only one caller wins; a claim older than {@code stale} counts as a crashed attempt. LINK_FAILED and INVOICED are never taken. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopStay s SET s.invoiceState = 'CLAIMED', s.invoiceClaimedAt = :now, s.invoiceError = NULL WHERE s.tenantId = :tenantId AND s.id = :id AND s.status = 'CHECKED_OUT' AND s.accountId IS NOT NULL "
            + "AND (s.invoiceState IN ('PENDING','FAILED') OR (s.invoiceState = 'CLAIMED' AND s.invoiceClaimedAt < :stale))")
    int claimForInvoicing(TenantId tenantId, UUID id, Instant now, Instant stale);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopStay s SET s.invoiceState = 'INVOICED', s.invoiceId = :invoiceId, s.invoiceNumber = :number, s.invoiceError = NULL WHERE s.tenantId = :tenantId AND s.id = :id")
    int markInvoiced(TenantId tenantId, UUID id, UUID invoiceId, String number);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopStay s SET s.invoiceState = 'FAILED', s.invoiceError = :message WHERE s.tenantId = :tenantId AND s.id = :id AND s.invoiceState = 'CLAIMED'")
    int markFailed(TenantId tenantId, UUID id, String message);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelTruckStopStay s SET s.invoiceState = 'LINK_FAILED', s.invoiceId = :invoiceId, s.invoiceNumber = :number, s.invoiceError = :message WHERE s.tenantId = :tenantId AND s.id = :id")
    int markLinkFailed(TenantId tenantId, UUID id, UUID invoiceId, String number, String message);

    /** Stays charged to an account that left within [from, to], oldest first. */
    @Query("SELECT s FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId AND s.accountId = :accountId AND s.status = 'CHECKED_OUT' AND s.actualDepartureDate >= :from AND s.actualDepartureDate <= :to ORDER BY s.actualDepartureDate, s.createdAt")
    List<FuelTruckStopStay> findChargedToAccount(TenantId tenantId, UUID accountId, LocalDate from, LocalDate to);
}
