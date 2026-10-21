package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueAttributeValue;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CatalogueAttributeValueRepository extends JpaRepository<CatalogueAttributeValue, UUID> {

    @Query("SELECT v FROM CatalogueAttributeValue v WHERE v.tenantId = :tenantId AND v.itemId = :itemId")
    List<CatalogueAttributeValue> findForItem(TenantId tenantId, UUID itemId);

    @Query("SELECT COUNT(v) FROM CatalogueAttributeValue v WHERE v.tenantId = :tenantId AND v.definitionId = :definitionId")
    long countForDefinition(TenantId tenantId, UUID definitionId);
}
