package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseItemRepository extends JpaRepository<WhseItem, UUID> {

    @Query("SELECT i FROM WhseItem i WHERE i.tenantId = :tenantId AND i.clientId = :clientId AND i.deletedAt IS NULL ORDER BY i.sku ASC")
    Page<WhseItem> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT i FROM WhseItem i WHERE i.tenantId = :tenantId AND i.clientId = :clientId AND i.deletedAt IS NULL ORDER BY i.sku ASC")
    List<WhseItem> findAllActiveForClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT i FROM WhseItem i WHERE i.tenantId = :tenantId AND i.id = :id AND i.deletedAt IS NULL")
    Optional<WhseItem> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM WhseItem i WHERE i.tenantId = :tenantId AND i.clientId = :clientId AND i.sku = :sku AND i.deletedAt IS NULL")
    Optional<WhseItem> findActiveByClientAndSku(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, @Param("sku") String sku);
}
