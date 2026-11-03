package za.co.handyflow.platform.secureaccess.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.secureaccess.domain.model.SecureShareItem;

import java.util.List;
import java.util.UUID;

public interface SecureShareItemRepository extends JpaRepository<SecureShareItem, UUID> {

    @Query("SELECT i FROM SecureShareItem i WHERE i.tenantId = :tenantId AND i.shareId = :shareId ORDER BY i.sortOrder")
    List<SecureShareItem> findByShare(@Param("tenantId") UUID tenantId, @Param("shareId") UUID shareId);
}
