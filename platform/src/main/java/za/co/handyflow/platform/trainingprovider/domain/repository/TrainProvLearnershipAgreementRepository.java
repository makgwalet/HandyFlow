package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnershipAgreement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnershipAgreementRepository extends JpaRepository<TrainProvLearnershipAgreement, UUID> {

    @Query("""
        SELECT a FROM TrainProvLearnershipAgreement a WHERE a.tenantId = :#{#tenantId.value}
        AND (CAST(:enrolmentId AS uuid) IS NULL OR a.enrolmentId = :enrolmentId)
        AND (:status IS NULL OR a.status = :status)
        ORDER BY a.agreementNo DESC
        """)
    List<TrainProvLearnershipAgreement> search(TenantId tenantId, UUID enrolmentId, String status);

    @Query("SELECT a FROM TrainProvLearnershipAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvLearnershipAgreement> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(a) > 0 FROM TrainProvLearnershipAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.enrolmentId = :enrolmentId AND a.status IN ('DRAFT', 'SIGNED')")
    boolean liveExists(TenantId tenantId, UUID enrolmentId);
}
