package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPlacementBatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollAgencyPlacementBatchRepository extends JpaRepository<CollAgencyPlacementBatch, UUID> {

    @Query("SELECT b FROM CollAgencyPlacementBatch b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<CollAgencyPlacementBatch> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT b FROM CollAgencyPlacementBatch b WHERE b.tenantId = :tenantId AND b.clientId = :clientId ORDER BY b.placedDate DESC")
    List<CollAgencyPlacementBatch> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);
}
