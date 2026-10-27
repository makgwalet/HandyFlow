package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmCreditNoteRepository extends JpaRepository<FmCreditNote, UUID> {

    @Query("SELECT c FROM FmCreditNote c WHERE c.tenantId.value = :#{#tenantId.value} AND c.id = :id")
    Optional<FmCreditNote> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM FmCreditNote c WHERE c.tenantId.value = :#{#tenantId.value} AND c.invoiceId = :invoiceId ORDER BY c.createdAt ASC")
    List<FmCreditNote> findForInvoice(@Param("tenantId") TenantId tenantId, @Param("invoiceId") UUID invoiceId);

    /** Credit notes a client may see: approved ones only, newest first. */
    @Query("SELECT c FROM FmCreditNote c WHERE c.tenantId.value = :#{#tenantId.value} AND c.clientId = :clientId AND c.status = 'APPROVED' " +
            "ORDER BY c.issueDate DESC, c.createdAt DESC")
    List<FmCreditNote> findApprovedForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /** Net (VAT-exclusive) value of APPROVED credit notes issued to a client in a period — deducted from recognised revenue. */
    @Query("SELECT SUM(c.subtotal) FROM FmCreditNote c WHERE c.tenantId.value = :#{#tenantId.value} AND c.clientId = :clientId " +
            "AND c.status = 'APPROVED' AND c.issueDate >= :from AND c.issueDate <= :to")
    BigDecimal sumApprovedSubtotalForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Cross-tenant: approved credit notes whose ledger posting is still owed and due for another attempt. */
    @Query("SELECT c FROM FmCreditNote c WHERE c.glStatus IN ('PENDING','FAILED') AND c.glAttempts < :maxAttempts " +
            "AND ((c.glLastAttemptAt IS NULL AND c.approvedAt < :cutoff) OR c.glLastAttemptAt < :cutoff)")
    List<FmCreditNote> findDueForGlRetry(@Param("cutoff") Instant cutoff, @Param("maxAttempts") int maxAttempts);

    @Query("SELECT c FROM FmCreditNote c WHERE c.tenantId.value = :#{#tenantId.value} AND c.glStatus = 'FAILED' ORDER BY c.approvedAt ASC")
    List<FmCreditNote> findGlExceptions(@Param("tenantId") TenantId tenantId);


    /** Credit notes in a status within client scope (e.g. DRAFT = awaiting approval). */
    @Query("SELECT COUNT(c) FROM FmCreditNote c WHERE c.tenantId.value = :#{#tenantId.value} AND c.status = :status " +
            "AND (:allClients = true OR c.clientId IN :clientIds)")
    long countByStatusInScope(@Param("tenantId") TenantId tenantId, @Param("status") String status,
                              @Param("allClients") boolean allClients, @Param("clientIds") Collection<UUID> clientIds);
}
