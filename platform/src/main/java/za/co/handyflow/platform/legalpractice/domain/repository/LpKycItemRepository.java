package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpKycItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpKycItemRepository extends JpaRepository<LpKycItem, UUID> {

    @Query("SELECT i FROM LpKycItem i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<LpKycItem> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT i FROM LpKycItem i WHERE i.tenantId = :tenantId AND i.profileId = :profileId ORDER BY i.verifiedOn, i.kind")
    List<LpKycItem> findForProfile(TenantId tenantId, UUID profileId);
}
