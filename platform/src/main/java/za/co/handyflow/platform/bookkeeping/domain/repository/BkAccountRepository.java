package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkAccount;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link BkAccount} has no {@code deletedAt} — a client's chart of
 * accounts line, once seeded/created, is never soft-deleted, matching the
 * entity's own bare shape (no soft-delete lifecycle method is declared on
 * it at all).
 */
public interface BkAccountRepository extends JpaRepository<BkAccount, UUID> {

    @Query("SELECT a FROM BkAccount a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<BkAccount> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM BkAccount a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId AND a.accountCode = :code")
    Optional<BkAccount> findByClientAndCode(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("code") String code);

    @Query("SELECT a FROM BkAccount a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId ORDER BY a.accountCode ASC")
    List<BkAccount> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);
}
