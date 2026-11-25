package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAssessmentInstrumentEvent;

import java.util.List;
import java.util.UUID;

public interface TrainProvAssessmentInstrumentEventRepository extends JpaRepository<TrainProvAssessmentInstrumentEvent, UUID> {

    @Query("SELECT e FROM TrainProvAssessmentInstrumentEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.instrumentId = :instrumentId ORDER BY e.actedAt")
    List<TrainProvAssessmentInstrumentEvent> findForInstrument(TenantId tenantId, UUID instrumentId);
}
