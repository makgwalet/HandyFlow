package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface BkProfileRepository extends JpaRepository<BkProfile, UUID> {

    @Query("SELECT p FROM BkProfile p WHERE p.tenantId = :#{#tenantId.value}")
    Optional<BkProfile> findByTenant(@Param("tenantId") TenantId tenantId);
}
