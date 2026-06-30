// security/domain/repository/ItineraryStopRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.ItineraryStop;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItineraryStopRepository extends JpaRepository<ItineraryStop, UUID> {

    @Query("""
        SELECT s FROM ItineraryStop s
        WHERE s.tenantId = :tenantId
        AND s.id = :id
        """)
    Optional<ItineraryStop> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT s FROM ItineraryStop s
        WHERE s.detailId = :detailId
        ORDER BY s.sequence
        """)
    List<ItineraryStop> findByDetail(UUID detailId);

    /** The current/next stop — first stop not yet departed. Used for the live status view. */
    @Query("""
        SELECT s FROM ItineraryStop s
        WHERE s.detailId = :detailId
        AND s.actualDeparture IS NULL
        ORDER BY s.sequence
        LIMIT 1
        """)
    Optional<ItineraryStop> findCurrentStop(UUID detailId);

    @Query("""
        SELECT COALESCE(MAX(s.sequence), 0) FROM ItineraryStop s
        WHERE s.detailId = :detailId
        """)
    int findMaxSequence(UUID detailId);
}
