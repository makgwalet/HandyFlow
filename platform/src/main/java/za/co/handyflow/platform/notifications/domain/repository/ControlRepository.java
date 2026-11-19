package za.co.handyflow.platform.notifications.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ControlRepository extends JpaRepository<Control, UUID> {

    @Query("SELECT c FROM Control c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<Control> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT c FROM Control c WHERE c.tenantId = :tenantId
        AND (:status IS NULL OR c.status = :status)
        AND (:includeRetired = TRUE OR c.status <> za.co.handyflow.platform.legalcompliance.domain.model.ControlStatus.RETIRED)
        ORDER BY c.reference
        """)
    List<Control> search(TenantId tenantId, ControlStatus status, boolean includeRetired);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
