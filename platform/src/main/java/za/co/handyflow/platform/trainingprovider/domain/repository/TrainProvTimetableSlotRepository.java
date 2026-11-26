package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvTimetableSlot;

import java.time.Instant;
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

    @Query("SELECT s FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value} AND s.venueId = :venueId AND s.status = 'SCHEDULED' AND s.startsAt < :to AND s.endsAt > :from")
    List<TrainProvTimetableSlot> liveForVenueBetween(TenantId tenantId, UUID venueId, Instant from, Instant to);

    @Query("SELECT s FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value} AND s.practitionerId = :practitionerId AND s.status = 'SCHEDULED' AND s.startsAt < :to AND s.endsAt > :from")
    List<TrainProvTimetableSlot> liveForPractitionerBetween(TenantId tenantId, UUID practitionerId, Instant from, Instant to);

    @Query("SELECT s FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value} AND s.cohortId = :cohortId AND s.status = 'SCHEDULED' AND s.startsAt < :to AND s.endsAt > :from")
    List<TrainProvTimetableSlot> liveForCohortBetween(TenantId tenantId, UUID cohortId, Instant from, Instant to);

    /** The learners (not enrolments) currently active in a cohort. One learner with two enrolments in two cohorts is still one person. */
    @Query("""
        SELECT DISTINCT e.learnerId FROM TrainProvCohortMember m, TrainProvLearnerEnrolment e
        WHERE m.tenantId = :#{#tenantId.value} AND e.tenantId = m.tenantId AND e.id = m.enrolmentId AND m.cohortId = :cohortId AND m.status = 'ACTIVE'
        """)
    List<UUID> activeLearnerIds(TenantId tenantId, UUID cohortId);

    /** Slots of other cohorts in the window that include any of these learners as active members. Each row is the slot and the learner. */
    @Query("""
        SELECT s, e.learnerId FROM TrainProvTimetableSlot s, TrainProvCohortMember m, TrainProvLearnerEnrolment e
        WHERE s.tenantId = :#{#tenantId.value} AND m.tenantId = s.tenantId AND e.tenantId = s.tenantId
        AND m.cohortId = s.cohortId AND m.status = 'ACTIVE' AND e.id = m.enrolmentId AND e.learnerId IN :learnerIds
        AND s.cohortId <> :cohortId AND s.status = 'SCHEDULED' AND s.startsAt < :to AND s.endsAt > :from
        """)
    List<Object[]> otherSlotsOfLearners(TenantId tenantId, UUID cohortId, List<UUID> learnerIds, Instant from, Instant to);

    /** Waits for any other booking in progress for the same thing, until this transaction ends, so two requests cannot both pass the clash check. */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtextextended(:key, 0)) AS text)", nativeQuery = true)
    String lock(String key);
}
