// security/domain/repository/ArmouryLogRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.ArmouryLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ArmouryLogRepository extends JpaRepository<ArmouryLog, UUID> {

    @Query("""
        SELECT l FROM ArmouryLog l
        WHERE l.tenantId = :tenantId
        AND l.armouryId = :armouryId
        ORDER BY l.occurredAt DESC
        """)
    List<ArmouryLog> findByArmoury(TenantId tenantId, UUID armouryId);

    @Query("""
        SELECT l FROM ArmouryLog l
        WHERE l.tenantId = :tenantId
        AND l.guardId = :guardId
        ORDER BY l.occurredAt DESC
        """)
    List<ArmouryLog> findByGuard(TenantId tenantId, UUID guardId);

    /** The most recent log entry for a firearm — used to confirm current holder before a return. */
    @Query("""
        SELECT l FROM ArmouryLog l
        WHERE l.armouryId = :armouryId
        ORDER BY l.occurredAt DESC
        LIMIT 1
        """)
    java.util.Optional<ArmouryLog> findMostRecent(UUID armouryId);
}
