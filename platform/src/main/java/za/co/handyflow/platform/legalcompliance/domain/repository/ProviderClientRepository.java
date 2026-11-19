package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderClient;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderClientRepository extends JpaRepository<ProviderClient, UUID> {

    /** Provider side. Another provider's id simply is not found. */
    @Query("SELECT x FROM ProviderClient x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<ProviderClient> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM ProviderClient x WHERE x.tenantId = :tenantId ORDER BY x.clientName")
    List<ProviderClient> findAllForTenant(TenantId tenantId);

    /** Cross-tenant by design — the client's side of the handshake. Only rows whose client_tenant_id is the caller's own tenant. */
    @Query("SELECT x FROM ProviderClient x WHERE x.clientTenantId = :clientTenant ORDER BY x.createdAt DESC")
    List<ProviderClient> findByClientTenantAcrossTenants(UUID clientTenant);

    @Query("SELECT x FROM ProviderClient x WHERE x.clientTenantId = :clientTenant AND x.id = :id")
    Optional<ProviderClient> findForClientTenantAcrossTenants(UUID clientTenant, UUID id);

    /** Cross-tenant by design — the invite code is the only way for a client to find a relationship that does not yet name it. */
    @Query("SELECT x FROM ProviderClient x WHERE x.inviteHash = :hash")
    Optional<ProviderClient> findByInviteHashAcrossTenants(String hash);

    /** Cross-tenant — daily sweep of accepted clients whose review date has arrived. */
    @Query("SELECT x FROM ProviderClient x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.ProviderClientStatus.ACTIVE AND x.nextReviewDue IS NOT NULL AND x.nextReviewDue <= :today")
    List<ProviderClient> findReviewDueAcrossTenants(LocalDate today);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
