package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueAuditEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface CatalogueAuditEventRepository extends JpaRepository<CatalogueAuditEvent, UUID> {

    @Query("SELECT e FROM CatalogueAuditEvent e WHERE e.tenantId = :tenantId AND e.entityId = :entityId ORDER BY e.createdAt DESC")
    Page<CatalogueAuditEvent> history(TenantId tenantId, UUID entityId, Pageable pageable);
}
