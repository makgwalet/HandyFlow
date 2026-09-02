package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkBankAccount;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface BkBankAccountRepository extends JpaRepository<BkBankAccount, UUID> {

    @Query("SELECT b FROM BkBankAccount b WHERE b.tenantId = :#{#tenantId.value} AND b.id = :id AND b.deletedAt IS NULL")
    Optional<BkBankAccount> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT b FROM BkBankAccount b WHERE b.tenantId = :#{#tenantId.value} AND b.clientId = :clientId AND b.deletedAt IS NULL")
    Page<BkBankAccount> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);
}
