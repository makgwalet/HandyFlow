package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSlotResource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSlotResourceRepository extends JpaRepository<TrainProvSlotResource, UUID> {

    @Query("SELECT b FROM TrainProvSlotResource b WHERE b.tenantId = :#{#tenantId.value} AND b.id = :id")
    Optional<TrainProvSlotResource> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT b FROM TrainProvSlotResource b WHERE b.tenantId = :#{#tenantId.value} AND b.slotId = :slotId ORDER BY b.status, b.createdAt")
    List<TrainProvSlotResource> forSlot(TenantId tenantId, UUID slotId);

    /** Live bookings of a resource on slots that are still scheduled, from now on. Each row is the booking and its slot. */
    @Query("""
        SELECT b, s FROM TrainProvSlotResource b, TrainProvTimetableSlot s WHERE b.tenantId = :#{#tenantId.value} AND s.tenantId = b.tenantId AND s.id = b.slotId
        AND b.resourceId = :resourceId AND b.status = 'BOOKED' AND s.status = 'SCHEDULED' AND s.endsAt > :from ORDER BY s.startsAt
        """)
    List<Object[]> upcomingForResource(TenantId tenantId, UUID resourceId, Instant from);

    /** Live bookings of the resource on other scheduled slots that overlap the given moments. Each row is the booking and its slot. */
    @Query("""
        SELECT b, s FROM TrainProvSlotResource b, TrainProvTimetableSlot s WHERE b.tenantId = :#{#tenantId.value} AND s.tenantId = b.tenantId AND s.id = b.slotId
        AND b.resourceId = :resourceId AND b.status = 'BOOKED' AND s.status = 'SCHEDULED' AND s.id <> :slotId AND s.startsAt < :to AND s.endsAt > :from
        """)
    List<Object[]> overlapping(TenantId tenantId, UUID resourceId, UUID slotId, Instant from, Instant to);

    /** Waits for any other booking in progress for the same resource, until this transaction ends, so two requests cannot both pass the clash check. */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtextextended(:key, 0)) AS text)", nativeQuery = true)
    String lock(String key);
}
