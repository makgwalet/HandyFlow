package za.co.handyflow.platform.supplychain.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScQuotation;

public interface ScQuotationRepository extends JpaRepository<ScQuotation, UUID> {

    @Query("SELECT q FROM ScQuotation q WHERE q.tenantId = :tenantId AND q.id = :id")
    Optional<ScQuotation> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT q FROM ScQuotation q WHERE q.tenantId = :tenantId AND q.rfqId = :rfqId ORDER BY q.supplierName")
    List<ScQuotation> findByRfq(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId);

    /** One quotation per supplier per RFQ (a resubmission replaces it). */
    @Query("SELECT q FROM ScQuotation q WHERE q.tenantId = :tenantId AND q.rfqId = :rfqId AND q.supplierId = :supplierId")
    Optional<ScQuotation> findByRfqAndSupplier(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId,
                                               @Param("supplierId") UUID supplierId);

    @Query("SELECT COUNT(q) FROM ScQuotation q WHERE q.tenantId = :tenantId AND q.rfqId = :rfqId "
            + "AND q.status = za.co.handyflow.platform.supplychain.domain.enums.QuotationStatus.SUBMITTED")
    long countSubmitted(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId);
}
