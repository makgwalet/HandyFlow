package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemApproval;
import za.co.handyflow.platform.catalogue.domain.model.ItemApprovalStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueItemApprovalRepository extends JpaRepository<CatalogueItemApproval, UUID> {

    @Query("SELECT a FROM CatalogueItemApproval a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<CatalogueItemApproval> findInTenant(TenantId tenantId, UUID id);

    @Query("SELECT a FROM CatalogueItemApproval a WHERE a.tenantId = :tenantId AND a.itemId = :itemId AND a.status = za.co.handyflow.platform.catalogue.domain.model.ItemApprovalStatus.PENDING")
    Optional<CatalogueItemApproval> pendingFor(TenantId tenantId, UUID itemId);

    @Query("SELECT a FROM CatalogueItemApproval a WHERE a.tenantId = :tenantId AND a.itemId = :itemId ORDER BY a.requestedAt DESC")
    List<CatalogueItemApproval> forItem(TenantId tenantId, UUID itemId);

    @Query("SELECT a FROM CatalogueItemApproval a WHERE a.tenantId = :tenantId AND a.status = :status ORDER BY a.requestedAt DESC")
    List<CatalogueItemApproval> byStatus(TenantId tenantId, ItemApprovalStatus status);

    @Query("SELECT COUNT(a) FROM CatalogueItemApproval a WHERE a.tenantId = :tenantId AND a.status = za.co.handyflow.platform.catalogue.domain.model.ItemApprovalStatus.PENDING")
    long countPending(TenantId tenantId);
}
