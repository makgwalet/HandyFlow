package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyPlacementStageHistory;

import java.util.List;
import java.util.UUID;

public interface RecAgencyPlacementStageHistoryRepository extends JpaRepository<RecAgencyPlacementStageHistory, UUID> {

    @Query("SELECT h FROM RecAgencyPlacementStageHistory h WHERE h.placementId = :placementId ORDER BY h.changedAt ASC")
    List<RecAgencyPlacementStageHistory> findByPlacement(@Param("placementId") UUID placementId);
}