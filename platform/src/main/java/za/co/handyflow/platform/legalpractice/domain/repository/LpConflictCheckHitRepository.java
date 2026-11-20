package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.legalpractice.domain.model.LpConflictCheckHit;

public interface LpConflictCheckHitRepository extends JpaRepository<LpConflictCheckHit, UUID> {

    @Query("SELECT h FROM LpConflictCheckHit h WHERE h.tenantId = :tenantId AND h.checkId = :checkId ORDER BY h.subjectName, h.matchedName")
    List<LpConflictCheckHit> findByCheck(TenantId tenantId, UUID checkId);
}
