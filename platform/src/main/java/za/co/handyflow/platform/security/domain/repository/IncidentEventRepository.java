package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.IncidentEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, UUID> {

    @Query("SELECT e FROM IncidentEvent e WHERE e.tenantId = :tenantId AND e.incidentId = :incidentId ORDER BY e.at, e.id")
    List<IncidentEvent> findForIncident(TenantId tenantId, UUID incidentId);
}
