package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnerRepository extends JpaRepository<TrainProvLearner, UUID> {

    @Query("""
        SELECT l FROM TrainProvLearner l WHERE l.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR l.status = :status)
        AND (CAST(:clientId AS uuid) IS NULL OR l.clientId = :clientId)
        AND (CAST(:search AS string) IS NULL
             OR LOWER(CONCAT(l.firstNames, ' ', l.surname)) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(l.learnerNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR l.idNumber LIKE CONCAT('%', CAST(:search AS string), '%'))
        ORDER BY l.surname, l.firstNames
        """)
    List<TrainProvLearner> search(TenantId tenantId, String status, UUID clientId, String search);

    @Query("SELECT l FROM TrainProvLearner l WHERE l.tenantId = :#{#tenantId.value} AND l.id = :id")
    Optional<TrainProvLearner> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(l) > 0 FROM TrainProvLearner l WHERE l.tenantId = :#{#tenantId.value} AND l.status = 'ACTIVE'
        AND l.idType = :idType AND LOWER(l.idNumber) = LOWER(:idNumber) AND (CAST(:excludeId AS uuid) IS NULL OR l.id <> :excludeId)
        """)
    boolean identityTaken(TenantId tenantId, String idType, String idNumber, UUID excludeId);
}
