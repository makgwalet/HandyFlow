package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvClientRepository extends JpaRepository<TrainProvClient, UUID> {

    @Query("""
        SELECT c FROM TrainProvClient c
        WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL
        AND (:status IS NULL OR c.status = :status)
        AND (CAST(:search AS string) IS NULL OR LOWER(c.tradingName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        ORDER BY c.tradingName
        """)
    Page<TrainProvClient> findAllActive(TenantId tenantId, String status, String search, Pageable pageable);

    @Query("SELECT c FROM TrainProvClient c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'ACTIVE' AND c.deletedAt IS NULL ORDER BY c.tradingName")
    List<TrainProvClient> findAllActiveList(TenantId tenantId);

    @Query("SELECT c FROM TrainProvClient c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<TrainProvClient> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(c) FROM TrainProvClient c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL")
    long countByTenant(TenantId tenantId);
}
