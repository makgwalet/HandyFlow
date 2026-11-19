package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ChangeStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryChange;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface RegulatoryChangeRepository extends JpaRepository<RegulatoryChange, UUID> {

    @Query("SELECT c FROM RegulatoryChange c WHERE c.tenantId = :tenantId AND (:status IS NULL OR c.status = :status) ORDER BY c.createdAt DESC")
    Page<RegulatoryChange> findPageForTenant(TenantId tenantId, ChangeStatus status, Pageable pageable);

    @Query("SELECT c FROM RegulatoryChange c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<RegulatoryChange> findOneForTenant(TenantId tenantId, UUID id);
}
