package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.recruiter.domain.model.RecApplication;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecApplicationRepository extends JpaRepository<RecApplication, UUID> {

    @Query("""
        SELECT a FROM RecApplication a
        WHERE a.jobId = :jobId
        AND (:stage IS NULL OR a.stage = :stage)
        ORDER BY a.appliedAt DESC
        """)
    Page<RecApplication> findByJob(UUID jobId, String stage, Pageable pageable);

    @Query("""
        SELECT a FROM RecApplication a
        WHERE a.tenantId = :tenantId
        AND (:stage IS NULL OR a.stage = :stage)
        ORDER BY a.appliedAt DESC
        """)
    Page<RecApplication> findAll(TenantId tenantId, String stage, Pageable pageable);

    Optional<RecApplication> findByIdAndTenantId(UUID id, TenantId tenantId);

    Optional<RecApplication> findByJobIdAndApplicantId(UUID jobId, UUID applicantId);

    List<RecApplication> findByApplicantId(UUID applicantId);

    @Query("""
        SELECT COUNT(a) FROM RecApplication a
        WHERE a.tenantId = :tenantId AND a.stage = :stage
        """)
    long countByStage(TenantId tenantId, String stage);
}
