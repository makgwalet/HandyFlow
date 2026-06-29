// security/domain/repository/RotationAssignmentRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.RotationAssignment;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RotationAssignmentRepository extends JpaRepository<RotationAssignment, UUID> {

    /** The guard's current open-ended assignment (if any). */
    @Query("""
        SELECT a FROM RotationAssignment a
        WHERE a.guardId = :guardId
        AND a.tenantId = :tenantId
        AND a.endsAt IS NULL
        """)
    Optional<RotationAssignment> findOpenAssignment(TenantId tenantId, UUID guardId);

    /** All guards assigned to a pattern (for schedule generation). */
    @Query("""
        SELECT a FROM RotationAssignment a
        WHERE a.patternId = :patternId
        AND a.tenantId = :tenantId
        AND a.endsAt IS NULL
        """)
    List<RotationAssignment> findActiveByPattern(TenantId tenantId, UUID patternId);

    /**
     * All assignments active on a given date — used by the schedule generator
     * to materialise shifts for that date.
     */
    @Query("""
        SELECT a FROM RotationAssignment a
        WHERE a.tenantId = :tenantId
        AND a.startsAt <= :date
        AND (a.endsAt IS NULL OR a.endsAt >= :date)
        """)
    List<RotationAssignment> findActiveOnDate(TenantId tenantId, LocalDate date);
}
