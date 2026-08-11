// security/domain/repository/GuardDocumentRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardDocument;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuardDocumentRepository extends JpaRepository<GuardDocument, UUID> {

    @Query("""
        SELECT d FROM GuardDocument d
        WHERE d.tenantId = :tenantId
        AND d.id = :id
        AND d.deletedAt IS NULL
        """)
    Optional<GuardDocument> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT d FROM GuardDocument d
        WHERE d.tenantId = :tenantId
        AND d.guardId = :guardId
        AND d.deletedAt IS NULL
        ORDER BY d.category, d.createdAt DESC
        """)
    List<GuardDocument> findActiveForGuard(TenantId tenantId, UUID guardId);
}