// security/domain/repository/DetailAssignmentRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.DetailAssignment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DetailAssignmentRepository extends JpaRepository<DetailAssignment, UUID> {

    @Query("""
        SELECT a FROM DetailAssignment a
        WHERE a.tenantId = :tenantId
        AND a.id = :id
        """)
    Optional<DetailAssignment> findByTenantAndId(TenantId tenantId, UUID id);

    /** The full team roster for a detail — active assignments only. */
    @Query("""
        SELECT a FROM DetailAssignment a
        WHERE a.detailId = :detailId
        AND a.assignmentEnd IS NULL
        """)
    List<DetailAssignment> findActiveByDetail(UUID detailId);

    /** A guard's currently active CP assignments, across all details. */
    @Query("""
        SELECT a FROM DetailAssignment a
        WHERE a.tenantId = :tenantId
        AND a.guardId = :guardId
        AND a.assignmentEnd IS NULL
        """)
    List<DetailAssignment> findActiveByGuard(TenantId tenantId, UUID guardId);

    @Query("""
        SELECT COUNT(a) > 0 FROM DetailAssignment a
        WHERE a.detailId = :detailId
        AND a.guardId = :guardId
        AND a.assignmentEnd IS NULL
        """)
    boolean hasOpenAssignment(UUID detailId, UUID guardId);
}
