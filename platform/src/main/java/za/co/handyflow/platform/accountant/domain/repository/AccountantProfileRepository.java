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
}
