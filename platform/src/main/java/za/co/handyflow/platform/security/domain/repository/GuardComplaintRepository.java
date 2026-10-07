// security/domain/repository/GuardComplaintRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardComplaint;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuardComplaintRepository extends JpaRepository<GuardComplaint, UUID>, JpaSpecificationExecutor<GuardComplaint> {

    @Query("SELECT c FROM GuardComplaint c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<GuardComplaint> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT c FROM GuardComplaint c WHERE c.tenantId = :tenantId AND c.guardId = :guardId ORDER BY c.occurredOn DESC, c.createdAt DESC")
    List<GuardComplaint> findForGuard(TenantId tenantId, UUID guardId);
}
