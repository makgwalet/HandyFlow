package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoicePayment;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmInvoicePaymentRepository extends JpaRepository<FmInvoicePayment, UUID> {

    @Query("SELECT p FROM FmInvoicePayment p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<FmInvoicePayment> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM FmInvoicePayment p WHERE p.tenantId = :#{#tenantId.value} AND p.invoiceId = :invoiceId " +
            "ORDER BY p.paymentDate ASC, p.createdAt ASC")
    List<FmInvoicePayment> findForInvoice(@Param("tenantId") TenantId tenantId, @Param("invoiceId") UUID invoiceId);

    /** Cross-tenant: payments whose ledger posting is still owed and due for another attempt. */
    @Query("SELECT p FROM FmInvoicePayment p WHERE p.glStatus IN ('PENDING','FAILED') AND p.glAttempts < :maxAttempts " +
            "AND ((p.glLastAttemptAt IS NULL AND p.createdAt < :cutoff) OR p.glLastAttemptAt < :cutoff)")
    List<FmInvoicePayment> findDueForGlRetry(@Param("cutoff") Instant cutoff, @Param("maxAttempts") int maxAttempts);

    @Query("SELECT p FROM FmInvoicePayment p WHERE p.tenantId = :#{#tenantId.value} AND p.glStatus = 'FAILED' ORDER BY p.createdAt ASC")
    List<FmInvoicePayment> findGlExceptions(@Param("tenantId") TenantId tenantId);
}