package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpDocumentEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface LpDocumentEventRepository extends JpaRepository<LpDocumentEvent, UUID> {

    /** Newest first. */
    @Query("SELECT e FROM LpDocumentEvent e WHERE e.tenantId = :tenantId AND e.documentId = :documentId ORDER BY e.occurredAt DESC")
    List<LpDocumentEvent> findForDocument(TenantId tenantId, UUID documentId, Pageable pageable);
}
