package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractClause;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractClauseRepository extends JpaRepository<ContractClause, UUID> {

    Optional<ContractClause> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT c FROM ContractClause c WHERE c.tenantId = :tenantId AND (:category IS NULL OR c.category = :category) "
            + "AND (:status IS NULL OR c.status = :status) ORDER BY c.title, c.versionNo DESC")
    List<ContractClause> search(UUID tenantId, String category, String status);

    Optional<ContractClause> findFirstByTenantIdAndFamilyIdAndStatus(UUID tenantId, UUID familyId, String status);

    @Query("SELECT COALESCE(MAX(c.versionNo), 0) FROM ContractClause c WHERE c.tenantId = :tenantId AND c.familyId = :familyId")
    int latestVersionNo(UUID tenantId, UUID familyId);
}
