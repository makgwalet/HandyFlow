package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId ORDER BY i.createdAt DESC")
    List<Incident> findByTenantIdOrderByCreatedAtDesc(TenantId tenantId);

    @Query("SELECT i FROM Incident i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<Incident> findByIdAndTenantId(UUID id, TenantId tenantId);
}