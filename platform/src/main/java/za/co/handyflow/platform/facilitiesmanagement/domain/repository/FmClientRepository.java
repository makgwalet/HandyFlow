package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface FmClientRepository extends JpaRepository<FmClient, UUID> {

    @Query("SELECT c FROM FmClient c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<FmClient> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM FmClient c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL")
    Page<FmClient> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    /** Scope-aware list. {@code clientIds} must never be empty (the access policy guarantees a placeholder). */
    @Query("SELECT c FROM FmClient c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL " +
            "AND (:allClients = true OR c.id IN :clientIds)")
    Page<FmClient> findAllActiveInScope(@Param("tenantId") TenantId tenantId, @Param("allClients") boolean allClients,
                                        @Param("clientIds") Collection<UUID> clientIds, Pageable pageable);
}