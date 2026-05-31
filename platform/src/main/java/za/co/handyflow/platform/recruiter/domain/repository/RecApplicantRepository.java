package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.recruiter.domain.model.RecApplicant;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface RecApplicantRepository extends JpaRepository<RecApplicant, UUID> {
    Optional<RecApplicant> findByTenantIdAndEmail(TenantId tenantId, String email);
    Optional<RecApplicant> findByPortalToken(String token);
}
