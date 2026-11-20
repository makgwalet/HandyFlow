package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpWallDenial;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface LpWallDenialRepository extends JpaRepository<LpWallDenial, UUID> {

    @Query("SELECT d FROM LpWallDenial d WHERE d.tenantId = :tenantId ORDER BY d.deniedAt DESC")
    Page<LpWallDenial> recent(TenantId tenantId, Pageable pageable);
}
