package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpDocument;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpDocumentRepository extends JpaRepository<LpDocument, UUID> {

    @Query("SELECT d FROM LpDocument d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<LpDocument> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT d FROM LpDocument d WHERE d.tenantId = :tenantId AND d.matterId = :matterId ORDER BY d.status ASC, d.title ASC")
    List<LpDocument> findForMatter(TenantId tenantId, UUID matterId);

    /** Across the firm, leaving out matters and clients a wall bars the caller from. An empty category or search means no filter; pattern is already %text%. */
    @Query("SELECT d FROM LpDocument d WHERE d.tenantId = :tenantId AND d.matterId NOT IN :barredMatterIds AND d.clientId NOT IN :barredClientIds "
            + "AND (:includeArchived = true OR d.status = 'ACTIVE') AND (:category = '' OR d.category = :category) AND LOWER(d.title) LIKE :pattern "
            + "ORDER BY d.updatedAt DESC")
    Page<LpDocument> findFirmExcluding(TenantId tenantId, List<UUID> barredMatterIds, List<UUID> barredClientIds, boolean includeArchived, String category, String pattern, Pageable pageable);
}
