package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScGoodsReceipt;

import java.util.Optional;
import java.util.UUID;

public interface ScGoodsReceiptRepository extends JpaRepository<ScGoodsReceipt, UUID> {

    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId ORDER BY g.createdAt DESC")
    Page<ScGoodsReceipt> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<ScGoodsReceipt> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(g.receiptNumber, 4) AS int)), 0) FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId")
    int findMaxReceiptSequence(@Param("tenantId") UUID tenantId);
}
