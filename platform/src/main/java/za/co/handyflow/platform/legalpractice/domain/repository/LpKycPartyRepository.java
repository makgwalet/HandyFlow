package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpKycParty;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpKycPartyRepository extends JpaRepository<LpKycParty, UUID> {

    @Query("SELECT p FROM LpKycParty p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<LpKycParty> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT p FROM LpKycParty p WHERE p.tenantId = :tenantId AND p.profileId = :profileId ORDER BY p.role, p.name")
    List<LpKycParty> findForProfile(TenantId tenantId, UUID profileId);
}
