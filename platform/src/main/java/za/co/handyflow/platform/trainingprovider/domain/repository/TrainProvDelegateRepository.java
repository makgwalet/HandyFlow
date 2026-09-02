package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvDelegateRepository extends JpaRepository<TrainProvDelegate, UUID> {

    @Query("""
        SELECT d FROM TrainProvDelegate d
        WHERE d.tenantId = :#{#tenantId.value} AND d.deletedAt IS NULL
        AND (:clientId IS NULL OR d.clientId = :clientId)
        AND (CAST(:search AS string) IS NULL OR LOWER(d.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        ORDER BY d.fullName
        """)
    Page<TrainProvDelegate> findAllActive(TenantId tenantId, UUID clientId, String search, Pageable pageable);

    @Query("SELECT d FROM TrainProvDelegate d WHERE d.tenantId = :#{#tenantId.value} AND d.id = :id AND d.deletedAt IS NULL")
    Optional<TrainProvDelegate> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT d FROM TrainProvDelegate d WHERE d.tenantId = :#{#tenantId.value} AND d.clientId = :clientId AND d.deletedAt IS NULL ORDER BY d.fullName")
    List<TrainProvDelegate> findAllActiveForClient(TenantId tenantId, UUID clientId);
}
