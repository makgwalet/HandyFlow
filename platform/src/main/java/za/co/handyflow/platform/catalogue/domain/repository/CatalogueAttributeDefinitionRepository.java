package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueAttributeDefinition;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueAttributeDefinitionRepository extends JpaRepository<CatalogueAttributeDefinition, UUID> {

    @Query("SELECT d FROM CatalogueAttributeDefinition d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL ORDER BY d.sortOrder, d.label")
    List<CatalogueAttributeDefinition> findAllLive(TenantId tenantId);

    @Query("SELECT d FROM CatalogueAttributeDefinition d WHERE d.tenantId = :tenantId AND d.id = :id AND d.deletedAt IS NULL")
    Optional<CatalogueAttributeDefinition> findLiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(d) > 0 FROM CatalogueAttributeDefinition d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL AND LOWER(d.code) = LOWER(:code)")
    boolean codeTaken(TenantId tenantId, String code);

    @Query("SELECT COUNT(d) FROM CatalogueAttributeDefinition d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL AND d.categoryId = :categoryId")
    long countForCategory(TenantId tenantId, UUID categoryId);
}
