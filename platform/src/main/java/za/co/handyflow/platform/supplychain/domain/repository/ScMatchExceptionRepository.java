package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScMatchException;

import java.util.List;
import java.util.UUID;

public interface ScMatchExceptionRepository extends JpaRepository<ScMatchException, UUID> {

    @Query("SELECT e FROM ScMatchException e WHERE e.tenantId = :tenantId AND e.supplierInvoiceId = :invoiceId ORDER BY e.createdAt DESC")
    List<ScMatchException> findAllForInvoice(@Param("tenantId") UUID tenantId, @Param("invoiceId") UUID invoiceId);

    @Query("SELECT e FROM ScMatchException e WHERE e.tenantId = :tenantId AND e.supplierInvoiceId = :invoiceId AND e.status = 'OPEN'")
    List<ScMatchException> findOpenForInvoice(@Param("tenantId") UUID tenantId, @Param("invoiceId") UUID invoiceId);

    @Query("SELECT COUNT(e) FROM ScMatchException e WHERE e.tenantId = :tenantId AND e.supplierInvoiceId = :invoiceId AND e.status = 'OPEN' AND e.exceptionType IN :types")
    long countOpenOfTypes(@Param("tenantId") UUID tenantId, @Param("invoiceId") UUID invoiceId,
                          @Param("types") java.util.Collection<za.co.handyflow.platform.supplychain.domain.enums.MatchExceptionType> types);
}
