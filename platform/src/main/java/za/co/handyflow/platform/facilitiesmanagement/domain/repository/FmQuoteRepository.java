package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuote;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmQuoteRepository extends JpaRepository<FmQuote, UUID> {

    @Query("SELECT q FROM FmQuote q WHERE q.tenantId.value = :#{#tenantId.value} AND q.id = :id")
    Optional<FmQuote> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** Staff list: {@code allClients=false} limits to {@code clientIds} (never empty; the policy supplies a placeholder). */
    @Query("SELECT q FROM FmQuote q WHERE q.tenantId.value = :#{#tenantId.value} AND (:status IS NULL OR q.status = :status) " +
            "AND (:clientId IS NULL OR q.clientId = :clientId) " +
            "AND (:allClients = true OR q.clientId IN :clientIds) ORDER BY q.createdAt DESC")
    Page<FmQuote> findAllInScope(@Param("tenantId") TenantId tenantId, @Param("status") String status, @Param("clientId") UUID clientId,
                                 @Param("allClients") boolean allClients, @Param("clientIds") Collection<UUID> clientIds, Pageable pageable);

    /** What a client contact may see: everything that has been sent, newest first. Drafts never leave the staff side. */
    @Query("SELECT q FROM FmQuote q WHERE q.tenantId.value = :#{#tenantId.value} AND q.clientId = :clientId AND q.status <> 'DRAFT' " +
            "ORDER BY q.sentAt DESC")
    List<FmQuote> findVisibleForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /** Cross-tenant daily sweep: SENT quotes whose validity ended before {@code today}. */
    @Query("SELECT q FROM FmQuote q WHERE q.status = 'SENT' AND q.validUntil < :today")
    List<FmQuote> findSentExpiredAcrossTenants(@Param("today") LocalDate today);
}
