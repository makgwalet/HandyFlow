package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnerDocumentRepository extends JpaRepository<TrainProvLearnerDocument, UUID> {

    @Query("""
        SELECT d FROM TrainProvLearnerDocument d WHERE d.tenantId = :#{#tenantId.value}
        AND (CAST(:enrolmentId AS uuid) IS NULL OR d.enrolmentId = :enrolmentId)
        AND (CAST(:learnerId AS uuid) IS NULL OR d.learnerId = :learnerId)
        AND (:docType IS NULL OR d.docType = :docType)
        AND (:status IS NULL OR d.status = :status)
        ORDER BY d.documentNo DESC
        """)
    List<TrainProvLearnerDocument> search(TenantId tenantId, UUID enrolmentId, UUID learnerId, String docType, String status);

    @Query("SELECT d FROM TrainProvLearnerDocument d WHERE d.tenantId = :#{#tenantId.value} AND d.id = :id")
    Optional<TrainProvLearnerDocument> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(d) > 0 FROM TrainProvLearnerDocument d WHERE d.tenantId = :#{#tenantId.value} AND d.enrolmentId = :enrolmentId AND d.docType = :docType AND d.status = 'ISSUED'")
    boolean liveExists(TenantId tenantId, UUID enrolmentId, String docType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TrainProvLearnerDocument d SET d.learnerId = :toId, d.version = d.version + 1 WHERE d.tenantId = :#{#tenantId.value} AND d.learnerId = :fromId")
    int moveLearner(TenantId tenantId, UUID fromId, UUID toId);
}
