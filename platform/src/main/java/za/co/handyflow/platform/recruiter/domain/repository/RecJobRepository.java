package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.recruiter.domain.model.RecJob;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecJobRepository extends JpaRepository<RecJob, UUID> {

    @Query("""
        SELECT j FROM RecJob j
        WHERE j.tenantId = :tenantId
        AND j.deletedAt IS NULL
        AND (:status IS NULL OR j.status = :status)
        ORDER BY j.createdAt DESC
        """)
    Page<RecJob> findAll(TenantId tenantId, String status, Pageable pageable);

    Optional<RecJob> findByIdAndTenantId(UUID id, TenantId tenantId);

    // Public careers page — only OPEN jobs
    @Query("""
        SELECT j FROM RecJob j
        WHERE j.tenantId = :tenantId
        AND j.status = 'OPEN'
        AND j.deletedAt IS NULL
        ORDER BY j.createdAt DESC
        """)
    List<RecJob> findOpenJobs(TenantId tenantId);

    Optional<RecJob> findByTenantIdAndSlug(TenantId tenantId, String slug);

    @Query("""
        SELECT COUNT(j) FROM RecJob j
        WHERE j.tenantId = :tenantId
        AND j.status = :status
        AND j.deletedAt IS NULL
        """)
    long countByStatus(TenantId tenantId, String status);
}
