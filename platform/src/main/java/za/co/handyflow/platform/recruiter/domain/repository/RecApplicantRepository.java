package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.recruiter.domain.model.RecApplicant;
import za.co.handyflow.platform.shared.TenantId;

import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecApplicantRepository extends JpaRepository<RecApplicant, UUID> {
    Optional<RecApplicant> findByTenantIdAndEmail(TenantId tenantId, String email);
    Optional<RecApplicant> findByPortalToken(String token);

    /** Applicants whose own record has not changed since the cut-off and whose details have not been removed. The rules look at their applications. */
    @Query("SELECT a FROM RecApplicant a WHERE a.tenantId = :tenantId AND a.anonymisedAt IS NULL AND a.updatedAt < :cutoff ORDER BY a.updatedAt")
    List<RecApplicant> notChangedSince(TenantId tenantId, Instant cutoff);
}
