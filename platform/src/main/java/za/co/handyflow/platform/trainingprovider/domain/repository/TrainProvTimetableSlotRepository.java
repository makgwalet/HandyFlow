package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvTimetableSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvTimetableSlotRepository extends JpaRepository<TrainProvTimetableSlot, UUID> {

    @Query("SELECT s FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value} AND s.cohortId = :cohortId ORDER BY s.slotDate, s.startTime")
    List<TrainProvTimetableSlot> findForCohort(TenantId tenantId, UUID cohortId);

    @Query("SELECT s FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id")
    Optional<TrainProvTimetableSlot> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT s FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value}
        AND s.slotDate >= :from AND s.slotDate <= :to
        AND (CAST(:venueId AS uuid) IS NULL OR s.venueId = :venueId)
        AND (CAST(:practitionerId AS uuid) IS NULL OR s.practitionerId = :practitionerId)
        AND (CAST(:cohortId AS uuid) IS NULL OR s.cohortId = :cohortId)
        ORDER BY s.slotDate, s.startTime
        """)
    List<TrainProvTimetableSlot> window(TenantId tenantId, LocalDate from, LocalDate to, UUID venueId, UUID practitionerId, UUID cohortId);

    @Query("SELECT s FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value} AND s.slotDate = :day AND s.venueId = :venueId AND s.status = 'SCHEDULED'")
    List<TrainProvTimetableSlot> liveForVenueOn(TenantId tenantId, UUID venueId, LocalDate day);

    @Query("SELECT s FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value} AND s.slotDate = :day AND s.practitionerId = :practitionerId AND s.status = 'SCHEDULED'")
    List<TrainProvTimetableSlot> liveForPractitionerOn(TenantId tenantId, UUID practitionerId, LocalDate day);
}
