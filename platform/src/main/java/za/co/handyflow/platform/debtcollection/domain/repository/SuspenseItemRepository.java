package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseItem;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseKind;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuspenseItemRepository extends JpaRepository<SuspenseItem, UUID> {

    @Query("SELECT s FROM SuspenseItem s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<SuspenseItem> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT s FROM SuspenseItem s WHERE s.tenantId = :tenantId AND s.status = :status ORDER BY s.createdAt ASC")
    List<SuspenseItem> findByStatus(TenantId tenantId, SuspenseStatus status);

    /** Provider retries must not park the same event twice. */
    @Query("SELECT s FROM SuspenseItem s WHERE s.tenantId = :tenantId AND s.kind = :kind AND s.externalReference = :ref")
    Optional<SuspenseItem> findByKindAndReference(TenantId tenantId, SuspenseKind kind, String ref);
}
