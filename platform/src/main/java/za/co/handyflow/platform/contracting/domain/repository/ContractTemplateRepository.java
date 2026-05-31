package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, UUID> {

    @Query("SELECT t FROM ContractTemplate t WHERE t.tenantId = :#{#tenantId.value} AND t.deletedAt IS NULL AND t.active = true ORDER BY t.contractType, t.name")
    List<ContractTemplate> findAllActive(TenantId tenantId);

    @Query("SELECT t FROM ContractTemplate t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id AND t.deletedAt IS NULL")
    Optional<ContractTemplate> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(t) FROM ContractTemplate t WHERE t.tenantId = :#{#tenantId.value} AND t.isSystem = true")
    long countSystemTemplates(TenantId tenantId);
}