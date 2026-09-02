package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FmClientRepository extends JpaRepository<FmClient, UUID> {

    @Query("SELECT c FROM FmClient c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<FmClient> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM FmClient c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL")
    Page<FmClient> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);
}
