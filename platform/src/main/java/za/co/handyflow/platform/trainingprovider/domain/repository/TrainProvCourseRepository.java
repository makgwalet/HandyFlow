package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCourse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvCourseRepository extends JpaRepository<TrainProvCourse, UUID> {

    @Query("""
        SELECT c FROM TrainProvCourse c
        WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL
        AND (:status IS NULL OR c.status = :status)
        AND (CAST(:search AS string) IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        ORDER BY c.title
        """)
    Page<TrainProvCourse> findAllActive(TenantId tenantId, String status, String search, Pageable pageable);

    @Query("SELECT c FROM TrainProvCourse c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'ACTIVE' AND c.deletedAt IS NULL ORDER BY c.title")
    List<TrainProvCourse> findAllActiveList(TenantId tenantId);

    @Query("SELECT c FROM TrainProvCourse c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<TrainProvCourse> findActiveById(TenantId tenantId, UUID id);
}
