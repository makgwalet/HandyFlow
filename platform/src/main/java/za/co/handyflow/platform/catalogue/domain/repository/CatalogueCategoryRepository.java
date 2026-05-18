package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueCategory;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueCategoryRepository extends JpaRepository<CatalogueCategory, UUID> {

    @Query("SELECT c FROM CatalogueCategory c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.sortOrder, c.name")
    List<CatalogueCategory> findAllActive(TenantId tenantId);

    @Query("SELECT c FROM CatalogueCategory c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<CatalogueCategory> findActiveById(TenantId tenantId, UUID id);
}
