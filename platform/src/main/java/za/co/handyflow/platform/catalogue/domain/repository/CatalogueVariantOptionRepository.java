package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueVariantOption;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CatalogueVariantOptionRepository extends JpaRepository<CatalogueVariantOption, UUID> {

    @Query("SELECT o FROM CatalogueVariantOption o WHERE o.tenantId = :tenantId AND o.parentItemId = :parentId ORDER BY o.sortOrder, o.name")
    List<CatalogueVariantOption> findForParent(TenantId tenantId, UUID parentId);

    @Modifying
    @Query("DELETE FROM CatalogueVariantOption o WHERE o.tenantId = :tenantId AND o.parentItemId = :parentId")
    void deleteForParent(TenantId tenantId, UUID parentId);
}
