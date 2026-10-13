package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.DebtHandoverLink;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface DebtHandoverLinkRepository extends JpaRepository<DebtHandoverLink, UUID> {

    @Query("SELECT l FROM DebtHandoverLink l WHERE l.tenantId = :tenantId AND l.handoverId = :handoverId")
    Optional<DebtHandoverLink> findByHandoverId(TenantId tenantId, UUID handoverId);
}
