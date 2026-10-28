package za.co.handyflow.platform.training.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingRequirement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingRequirementRepository extends JpaRepository<TrainingRequirement, UUID> {

    @Query("SELECT r FROM TrainingRequirement r WHERE r.tenantId = :#{#tenantId.value} ORDER BY r.createdAt")
    List<TrainingRequirement> findAllForTenant(TenantId tenantId);

    @Query("SELECT r FROM TrainingRequirement r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<TrainingRequirement> findByTenantAndId(TenantId tenantId, UUID id);
}
