package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectorProfileRepository extends JpaRepository<CollectorProfile, UUID> {

    @Query("SELECT p FROM CollectorProfile p WHERE p.tenantId = :tenantId AND p.userId = :userId")
    Optional<CollectorProfile> findByUserId(TenantId tenantId, UUID userId);

    @Query("SELECT p FROM CollectorProfile p WHERE p.tenantId = :tenantId ORDER BY p.userName")
    List<CollectorProfile> findAllForTenant(TenantId tenantId);
}
