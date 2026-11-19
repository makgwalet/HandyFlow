package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<Incident> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT i FROM Incident i WHERE i.tenantId = :tenantId
        AND (:status IS NULL OR i.status = :status)
        AND (:includeClosed = TRUE OR i.status <> za.co.handyflow.platform.legalcompliance.domain.model.IncidentStatus.CLOSED)
        ORDER BY i.discoveredAt DESC
        """)
    List<Incident> search(TenantId tenantId, IncidentStatus status, boolean includeClosed);

    /** Cross-tenant — daily sweep: personal-information incidents still awaiting a notification assessment. */
    @Query("SELECT i FROM Incident i WHERE i.personalInfoInvolved = TRUE AND i.notificationAssessedAt IS NULL AND i.status <> za.co.handyflow.platform.legalcompliance.domain.model.IncidentStatus.CLOSED")
    List<Incident> findUnassessedAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
