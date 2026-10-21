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

    UUID NIL = new UUID(0L, 0L);

    /** A name only has to be unique among siblings; {@code parentId} null means top level. */
    default boolean nameTaken(TenantId tenantId, String name, UUID parentId, UUID excludeId) {
        return siblingNameTaken(tenantId, name, parentId == null ? NIL : parentId, excludeId, NIL);
    }

    /** {@code parentOrNil} is the parent id or the all-zero UUID for top level, so no null parameter reaches the query. */
    @Query("SELECT COUNT(c) > 0 FROM CatalogueCategory c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL "
            + "AND LOWER(c.name) = LOWER(:name) AND COALESCE(c.parentId, :nil) = :parentOrNil AND c.id <> :excludeId")
    boolean siblingNameTaken(TenantId tenantId, String name, UUID parentOrNil, UUID excludeId, UUID nil);
}
