package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.LegalEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface LegalEventRepository extends JpaRepository<LegalEvent, UUID> {

    @Query("SELECT e FROM LegalEvent e WHERE e.tenantId = :tenantId AND e.subjectId = :subjectId ORDER BY e.createdAt ASC")
    List<LegalEvent> findBySubjectId(TenantId tenantId, UUID subjectId);
}
