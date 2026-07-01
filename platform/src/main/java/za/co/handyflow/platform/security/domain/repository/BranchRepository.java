// security/domain/repository/BranchRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Branch;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    @Query("SELECT b FROM Branch b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<Branch> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT b FROM Branch b WHERE b.tenantId = :tenantId AND b.active = true ORDER BY b.name")
    List<Branch> findActiveBranches(TenantId tenantId);

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.tenantId = :tenantId AND b.name = :name AND b.active = true")
    boolean existsByName(TenantId tenantId, String name);
}