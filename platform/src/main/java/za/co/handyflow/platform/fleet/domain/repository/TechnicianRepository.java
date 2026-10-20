package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Technician;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

    @Query("SELECT t FROM Technician t WHERE t.tenantId = :tenantId ORDER BY t.name")
    List<Technician> findAllForTenant(TenantId tenantId);

    @Query("SELECT t FROM Technician t WHERE t.tenantId = :tenantId AND t.active = true ORDER BY t.name")
    List<Technician> findActiveForTenant(TenantId tenantId);

    @Query("SELECT t FROM Technician t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<Technician> findByTenantAndId(TenantId tenantId, UUID id);
}