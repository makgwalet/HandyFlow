package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccountantProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountantProfileRepository extends JpaRepository<AccountantProfile, UUID> {

    @Query("SELECT p FROM AccountantProfile p WHERE p.tenantId = :tenantId")
    Optional<AccountantProfile> findByTenantId(@Param("tenantId") TenantId tenantId);

    /**
     * NEW: closes the "portal fee note PDF download" gap. Native SQL,
     * not JPQL — avoids needing to construct a TenantId object at all
     * (its exact constructor/factory API was never seen directly this
     * session, only ever received via TenantContext), by querying the
     * raw tenant_id column directly instead of the embedded JPA type.
     */
    @Query(value = "SELECT * FROM accountant_profiles WHERE tenant_id = :tenantId", nativeQuery = true)
    Optional<AccountantProfile> findByTenantIdRaw(@Param("tenantId") UUID tenantId);
}