package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkClient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface BkClientRepository extends JpaRepository<BkClient, UUID> {

    @Query("SELECT c FROM BkClient c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<BkClient> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM BkClient c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL")
    Page<BkClient> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);
}
