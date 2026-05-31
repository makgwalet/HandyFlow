package za.co.handyflow.platform.desk.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.desk.domain.model.DeskCategory;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface DeskCategoryRepository extends JpaRepository<DeskCategory, UUID> {
    List<DeskCategory> findByTenantIdAndActiveTrueOrderBySortOrderAsc(TenantId tenantId);
}
