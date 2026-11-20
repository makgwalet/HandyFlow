package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpKycProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpKycProfileRepository extends JpaRepository<LpKycProfile, UUID> {

    @Query("SELECT p FROM LpKycProfile p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<LpKycProfile> findActiveById(TenantId tenantId, UUID id);

    /** Every cycle for the client, newest first. */
    @Query("SELECT p FROM LpKycProfile p WHERE p.tenantId = :tenantId AND p.clientId = :clientId ORDER BY p.startedAt DESC")
    List<LpKycProfile> findForClient(TenantId tenantId, UUID clientId);

    /** Every cycle for these clients, newest first. The overview picks each client's latest and latest verified from this. */
    @Query("SELECT p FROM LpKycProfile p WHERE p.tenantId = :tenantId AND p.clientId IN :clientIds ORDER BY p.startedAt DESC")
    List<LpKycProfile> findForClients(TenantId tenantId, Collection<UUID> clientIds);
}
