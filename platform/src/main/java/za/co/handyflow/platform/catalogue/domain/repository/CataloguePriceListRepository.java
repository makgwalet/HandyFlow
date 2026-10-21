package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CataloguePriceList;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CataloguePriceListRepository extends JpaRepository<CataloguePriceList, UUID> {

    @Query("SELECT l FROM CataloguePriceList l WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL ORDER BY l.sortOrder, l.name")
    List<CataloguePriceList> findAllLive(TenantId tenantId);

    @Query("SELECT l FROM CataloguePriceList l WHERE l.tenantId = :tenantId AND l.id = :id AND l.deletedAt IS NULL")
    Optional<CataloguePriceList> findLiveById(TenantId tenantId, UUID id);

    @Query("SELECT l FROM CataloguePriceList l WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL AND l.defaultList = true")
    Optional<CataloguePriceList> findDefault(TenantId tenantId);

    @Query("SELECT COUNT(l) > 0 FROM CataloguePriceList l WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL AND LOWER(l.code) = LOWER(:code)")
    boolean codeTaken(TenantId tenantId, String code);

    @Query("SELECT COUNT(l) > 0 FROM CataloguePriceList l WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL AND LOWER(l.name) = LOWER(:name) AND l.id <> :excludeId")
    boolean nameTaken(TenantId tenantId, String name, UUID excludeId);
}
