package za.co.handyflow.platform.supplychain.domain.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScQuotationLine;

public interface ScQuotationLineRepository extends JpaRepository<ScQuotationLine, UUID> {

    @Query("SELECT l FROM ScQuotationLine l WHERE l.tenantId = :tenantId AND l.quotationId = :quotationId")
    List<ScQuotationLine> findByQuotation(@Param("tenantId") UUID tenantId, @Param("quotationId") UUID quotationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ScQuotationLine l WHERE l.tenantId = :tenantId AND l.quotationId = :quotationId")
    void deleteByQuotation(@Param("tenantId") UUID tenantId, @Param("quotationId") UUID quotationId);
}
