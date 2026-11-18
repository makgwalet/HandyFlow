package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.ContractTemplateLegalReview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractTemplateLegalReviewRepository extends JpaRepository<ContractTemplateLegalReview, UUID> {

    List<ContractTemplateLegalReview> findByTenantIdAndTemplateIdOrderByCreatedAtDesc(UUID tenantId, UUID templateId);

    /** Newest review of this exact template version, withdrawn or not. */
    Optional<ContractTemplateLegalReview> findFirstByTenantIdAndTemplateIdAndTemplateVersionOrderByCreatedAtDesc(
            UUID tenantId, UUID templateId, int templateVersion);

    Optional<ContractTemplateLegalReview> findByTenantIdAndIdAndTemplateId(UUID tenantId, UUID id, UUID templateId);
}
