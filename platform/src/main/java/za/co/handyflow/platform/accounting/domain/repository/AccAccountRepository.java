package za.co.handyflow.platform.accounting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.accounting.domain.model.AccAccount;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccAccountRepository extends JpaRepository<AccAccount, UUID> {

    @Query("SELECT a FROM AccAccount a WHERE a.tenantId = :#{#tenantId.value} AND a.active = true ORDER BY a.accountCode")
    List<AccAccount> findAllActive(TenantId tenantId);

    @Query("SELECT a FROM AccAccount a WHERE a.tenantId = :#{#tenantId.value} AND a.accountType = :type AND a.active = true ORDER BY a.accountCode")
    List<AccAccount> findByType(TenantId tenantId, String type);

    @Query("SELECT a FROM AccAccount a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<AccAccount> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(a) FROM AccAccount a WHERE a.tenantId = :#{#tenantId.value}")
    long countByTenant(TenantId tenantId);
}