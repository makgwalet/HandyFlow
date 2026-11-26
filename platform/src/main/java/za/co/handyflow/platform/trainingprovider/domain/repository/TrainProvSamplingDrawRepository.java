package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAssessmentAttempt;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSamplingDraw;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSamplingDrawRepository extends JpaRepository<TrainProvSamplingDraw, UUID> {

    @Query("SELECT d FROM TrainProvSamplingDraw d WHERE d.tenantId = :#{#tenantId.value} AND d.id = :id")
    Optional<TrainProvSamplingDraw> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT d FROM TrainProvSamplingDraw d WHERE d.tenantId = :#{#tenantId.value} AND (:status IS NULL OR d.status = :status) ORDER BY d.drawNo DESC")
    List<TrainProvSamplingDraw> search(TenantId tenantId, String status);

    /**
     * Decided, live attempts on moderated instruments in a window, within an optional scope, that are not already in a live draw.
     * Attempts that have already been moderated are left out: they have had their moderation.
     */
    @Query("""
        SELECT a FROM TrainProvAssessmentAttempt a WHERE a.tenantId = :#{#tenantId.value} AND a.status = 'RECORDED'
        AND EXISTS (SELECT 1 FROM TrainProvAssessmentDecision d WHERE d.tenantId = a.tenantId AND d.attemptId = a.id
                    AND d.decidedOn >= :from AND d.decidedOn <= :to)
        AND NOT EXISTS (SELECT 1 FROM TrainProvAssessmentModeration m WHERE m.tenantId = a.tenantId AND m.attemptId = a.id)
        AND NOT EXISTS (SELECT 1 FROM TrainProvSamplingDrawItem i JOIN TrainProvSamplingDraw w ON w.id = i.drawId
                        WHERE i.tenantId = a.tenantId AND i.attemptId = a.id AND w.status <> 'VOID')
        AND EXISTS (SELECT 1 FROM TrainProvAssessmentInstrument n WHERE n.tenantId = a.tenantId AND n.id = a.instrumentId AND n.requiresModeration = true
                    AND (:assessmentType IS NULL OR n.assessmentType = :assessmentType))
        AND (:cohortId IS NULL OR a.cohortId = :cohortId)
        AND (:programmeId IS NULL OR EXISTS (SELECT 1 FROM TrainProvCohort c WHERE c.tenantId = a.tenantId AND c.id = a.cohortId AND c.programmeId = :programmeId))
        ORDER BY a.attemptNo
        """)
    List<TrainProvAssessmentAttempt> population(TenantId tenantId, LocalDate from, LocalDate to, UUID programmeId, UUID cohortId, String assessmentType);

    /** True for each recent moderation in scope that raised a query, newest first. Page size is the look-back. */
    @Query("""
        SELECT CASE WHEN m.outcome = 'QUERIED' THEN true ELSE false END FROM TrainProvAssessmentModeration m
        JOIN TrainProvAssessmentAttempt a ON a.id = m.attemptId
        WHERE m.tenantId = :#{#tenantId.value}
        AND (:cohortId IS NULL OR a.cohortId = :cohortId)
        AND (:programmeId IS NULL OR EXISTS (SELECT 1 FROM TrainProvCohort c WHERE c.tenantId = a.tenantId AND c.id = a.cohortId AND c.programmeId = :programmeId))
        AND (:assessmentType IS NULL OR EXISTS (SELECT 1 FROM TrainProvAssessmentInstrument n WHERE n.tenantId = a.tenantId AND n.id = a.instrumentId AND n.assessmentType = :assessmentType))
        ORDER BY m.moderatedOn DESC, m.moderationNo DESC
        """)
    List<Boolean> recentQueries(TenantId tenantId, UUID programmeId, UUID cohortId, String assessmentType, Pageable page);

    /** How many moderations an assessor has had, and how many of those raised a query, over all time. */
    @Query("""
        SELECT COUNT(m) FROM TrainProvAssessmentModeration m JOIN TrainProvAssessmentAttempt a ON a.id = m.attemptId
        WHERE m.tenantId = :#{#tenantId.value} AND a.assessorId = :assessorId
        """)
    long moderationsFor(TenantId tenantId, UUID assessorId);

    @Query("""
        SELECT COUNT(m) FROM TrainProvAssessmentModeration m JOIN TrainProvAssessmentAttempt a ON a.id = m.attemptId
        WHERE m.tenantId = :#{#tenantId.value} AND a.assessorId = :assessorId AND m.outcome = 'QUERIED'
        """)
    long queriesFor(TenantId tenantId, UUID assessorId);
}
