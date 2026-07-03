// ─────────────────────────────────────────────────────────────────────────────
// ScGrLineRepository.java  (NEW — H-4 fix: record line detail on GR post)
// ─────────────────────────────────────────────────────────────────────────────
package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScGrLine;

import java.util.List;
import java.util.UUID;

public interface ScGrLineRepository extends JpaRepository<ScGrLine, UUID> {

    @Query("SELECT l FROM ScGrLine l WHERE l.goodsReceiptId = :grId ORDER BY l.id")
    List<ScGrLine> findByGoodsReceiptId(@Param("grId") UUID goodsReceiptId);

    @Query("SELECT l FROM ScGrLine l WHERE l.poLineId = :poLineId")
    List<ScGrLine> findByPoLineId(@Param("poLineId") UUID poLineId);
}