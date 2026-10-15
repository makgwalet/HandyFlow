package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthChecklistQuestion;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface EarthChecklistQuestionRepository extends JpaRepository<EarthChecklistQuestion, UUID> {

    @Query("SELECT q FROM EarthChecklistQuestion q WHERE q.tenantId = :tenantId AND q.versionId = :versionId ORDER BY q.position")
    List<EarthChecklistQuestion> findForVersion(TenantId tenantId, UUID versionId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM EarthChecklistQuestion q WHERE q.tenantId = :tenantId AND q.versionId = :versionId")
    void deleteForVersion(TenantId tenantId, UUID versionId);
}
