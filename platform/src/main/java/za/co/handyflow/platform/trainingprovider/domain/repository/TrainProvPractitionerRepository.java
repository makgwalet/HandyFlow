package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvPractitioner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvPractitionerRepository extends JpaRepository<TrainProvPractitioner, UUID> {

    @Query("""
        SELECT p FROM TrainProvPractitioner p WHERE p.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR p.status = :status)
        AND (CAST(:search AS string) IS NULL
             OR LOWER(CONCAT(p.firstNames, ' ', p.surname)) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(p.practitionerNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        ORDER BY p.surname, p.firstNames
        """)
    List<TrainProvPractitioner> search(TenantId tenantId, String status, String search);

    @Query("SELECT p FROM TrainProvPractitioner p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<TrainProvPractitioner> findForTenant(TenantId tenantId, UUID id);
}
