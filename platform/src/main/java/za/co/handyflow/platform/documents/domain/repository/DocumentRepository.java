package za.co.handyflow.platform.documents.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.documents.domain.model.Document;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("SELECT d FROM Document d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<Document> findOwned(TenantId tenantId, UUID id);

    @Query("SELECT d FROM Document d WHERE d.tenantId = :tenantId AND d.ownerType = :ownerType AND d.ownerId = :ownerId AND (:includeArchived = TRUE OR d.status = 'ACTIVE') ORDER BY d.category, d.title")
    List<Document> findForOwner(TenantId tenantId, String ownerType, UUID ownerId, boolean includeArchived);

    @Query("SELECT d FROM Document d WHERE d.tenantId = :tenantId AND d.ownerType IN :ownerTypes AND d.status = 'ACTIVE' AND d.expiresOn IS NOT NULL AND d.expiresOn <= :until ORDER BY d.expiresOn")
    List<Document> findExpiring(TenantId tenantId, Collection<String> ownerTypes, LocalDate until);

    @Query("SELECT DISTINCT d.tenantId FROM Document d WHERE d.ownerType IN :ownerTypes AND d.status = 'ACTIVE' AND d.expiresOn IS NOT NULL AND d.expiresOn <= :until")
    List<TenantId> findTenantsWithExpiring(Collection<String> ownerTypes, LocalDate until);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.tenantId = :tenantId AND d.ownerType = :ownerType AND d.ownerId = :ownerId AND d.category = :category AND d.status = 'ACTIVE'")
    long countActive(TenantId tenantId, String ownerType, UUID ownerId, String category);
}
