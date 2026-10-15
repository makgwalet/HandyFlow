package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthInspectionItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EarthInspectionItemRepository extends JpaRepository<EarthInspectionItem, UUID> {

    @Query("SELECT x FROM EarthInspectionItem x WHERE x.tenantId = :tenantId AND x.inspectionId IN :ids ORDER BY x.position")
    List<EarthInspectionItem> findForInspections(TenantId tenantId, Collection<UUID> ids);
}
