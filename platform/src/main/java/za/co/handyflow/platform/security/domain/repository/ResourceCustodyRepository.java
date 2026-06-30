// security/domain/repository/ResourceCustodyRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.ResourceCustody;

import java.util.List;
import java.util.UUID;

public interface ResourceCustodyRepository extends JpaRepository<ResourceCustody, UUID> {

    /** All resources still checked out by a guard — used to block clock-out. */
    @Query("""
        SELECT c FROM ResourceCustody c
        WHERE c.guardId = :guardId
        AND c.checkedInAt IS NULL
        """)
    List<ResourceCustody> findCheckedOutByGuard(UUID guardId);

    @Query("""
        SELECT c FROM ResourceCustody c
        WHERE c.sessionId = :sessionId
        """)
    List<ResourceCustody> findBySession(UUID sessionId);
}
