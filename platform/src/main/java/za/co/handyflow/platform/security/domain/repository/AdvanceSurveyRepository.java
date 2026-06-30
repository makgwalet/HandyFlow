// security/domain/repository/AdvanceSurveyRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.AdvanceSurvey;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdvanceSurveyRepository extends JpaRepository<AdvanceSurvey, UUID> {

    @Query("""
        SELECT s FROM AdvanceSurvey s
        WHERE s.tenantId = :tenantId
        AND s.id = :id
        """)
    Optional<AdvanceSurvey> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT s FROM AdvanceSurvey s
        WHERE s.itineraryStopId = :stopId
        ORDER BY s.surveyedAt DESC
        """)
    List<AdvanceSurvey> findByStop(UUID stopId);

    @Query("""
        SELECT COUNT(s) > 0 FROM AdvanceSurvey s
        WHERE s.itineraryStopId = :stopId
        AND s.allClear = true
        """)
    boolean hasAllClearSurvey(UUID stopId);
}
