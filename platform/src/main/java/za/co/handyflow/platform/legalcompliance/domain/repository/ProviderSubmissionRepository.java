package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderSubmission;
import za.co.handyflow.platform.legalcompliance.domain.model.SubmissionStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderSubmissionRepository extends JpaRepository<ProviderSubmission, UUID> {

    // ── Client side: rows of the caller's own tenant ──

    @Query("SELECT x FROM ProviderSubmission x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<ProviderSubmission> findForClient(TenantId tenantId, UUID id);

    @Query("SELECT x FROM ProviderSubmission x WHERE x.tenantId = :tenantId AND (:status IS NULL OR x.status = :status) ORDER BY x.createdAt DESC")
    List<ProviderSubmission> searchForClient(TenantId tenantId, SubmissionStatus status, Pageable page);

    @Query("SELECT COUNT(x) FROM ProviderSubmission x WHERE x.tenantId = :tenantId AND x.status IN (za.co.handyflow.platform.legalcompliance.domain.model.SubmissionStatus.SUBMITTED, za.co.handyflow.platform.legalcompliance.domain.model.SubmissionStatus.ACCEPTED)")
    long countOpenForClient(TenantId tenantId);

    // ── Provider side: cross-tenant by design, but only rows this provider itself sent, through one of its own relationships ──

    @Query("SELECT x FROM ProviderSubmission x WHERE x.providerTenantId = :providerTenant AND x.providerClientId = :relationshipId AND x.id = :id")
    Optional<ProviderSubmission> findForProvider(UUID providerTenant, UUID relationshipId, UUID id);

    @Query("SELECT x FROM ProviderSubmission x WHERE x.providerTenantId = :providerTenant AND x.providerClientId = :relationshipId ORDER BY x.createdAt DESC")
    List<ProviderSubmission> listForProvider(UUID providerTenant, UUID relationshipId, Pageable page);

    @Query("SELECT COUNT(x) FROM ProviderSubmission x WHERE x.providerTenantId = :providerTenant AND x.providerClientId = :relationshipId AND x.status = za.co.handyflow.platform.legalcompliance.domain.model.SubmissionStatus.SUBMITTED")
    long countUnansweredForProvider(UUID providerTenant, UUID relationshipId);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
