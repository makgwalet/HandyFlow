package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.legalpractice.domain.model.LpConflictCheckSubject;

public interface LpConflictCheckSubjectRepository extends JpaRepository<LpConflictCheckSubject, UUID> {

    @Query("SELECT s FROM LpConflictCheckSubject s WHERE s.tenantId = :tenantId AND s.checkId = :checkId ORDER BY s.name")
    List<LpConflictCheckSubject> findByCheck(TenantId tenantId, UUID checkId);
}
