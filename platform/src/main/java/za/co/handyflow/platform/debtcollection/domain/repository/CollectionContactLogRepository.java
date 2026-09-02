package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.CollectionContactLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CollectionContactLogRepository extends JpaRepository<CollectionContactLog, UUID> {

    // FIX: same embedded-TenantId vs SpEL-unwrap type mismatch as
    // RegulatoryObligationRepository (see its comment).
    @Query("""
        SELECT l FROM CollectionContactLog l WHERE l.tenantId = :tenantId AND l.caseId = :caseId
        ORDER BY l.contactDate DESC, l.createdAt DESC
        """)
    List<CollectionContactLog> findByCaseId(TenantId tenantId, UUID caseId);
}