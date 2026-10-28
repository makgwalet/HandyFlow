package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.recruiter.domain.model.RecApplication;
import za.co.handyflow.platform.recruiter.domain.model.RecApplicant;
import za.co.handyflow.platform.recruiter.domain.model.RecJob;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecApplicationRepository extends JpaRepository<RecApplication, UUID> {

    @Query("""
        SELECT a FROM RecApplication a
        WHERE a.tenantId = :tenantId AND a.jobId = :jobId
        AND (:stage IS NULL OR a.stage = :stage)
        ORDER BY a.appliedAt DESC
        """)
    Page<RecApplication> findByJob(TenantId tenantId, UUID jobId, String stage, Pageable pageable);

    @Query("""
        SELECT a FROM RecApplication a
        WHERE a.tenantId = :tenantId
        AND (:stage IS NULL OR a.stage = :stage)
        ORDER BY a.appliedAt DESC
        """)
    Page<RecApplication> findAll(TenantId tenantId, String stage, Pageable pageable);

    /** Applications whose candidate name, email or job title contains the text. {@code pattern} comes from RecruiterSearchRules.pattern. */
    @Query("""
        SELECT a FROM RecApplication a, RecApplicant p, RecJob j
        WHERE a.tenantId = :tenantId AND p.id = a.applicantId AND j.id = a.jobId
        AND (:jobId IS NULL OR a.jobId = :jobId)
        AND (:stage IS NULL OR a.stage = :stage)
        AND (LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE :pattern ESCAPE '\\'
             OR LOWER(p.email) LIKE :pattern ESCAPE '\\'
             OR LOWER(j.title) LIKE :pattern ESCAPE '\\')
        ORDER BY a.appliedAt DESC
        """)
    Page<RecApplication> search(TenantId tenantId, UUID jobId, String stage, String pattern, Pageable pageable);

    Optional<RecApplication> findByIdAndTenantId(UUID id, TenantId tenantId);

    /** Same, but the row stays locked until the transaction ends, so two requests for one application take turns. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM RecApplication a WHERE a.id = :id AND a.tenantId = :tenantId")
    Optional<RecApplication> lockByIdAndTenantId(UUID id, TenantId tenantId);

    Optional<RecApplication> findByJobIdAndApplicantId(UUID jobId, UUID applicantId);

    List<RecApplication> findByApplicantId(UUID applicantId);

    List<RecApplication> findByApplicantIdIn(java.util.Collection<UUID> applicantIds);

    @Query("""
        SELECT COUNT(a) FROM RecApplication a
        WHERE a.tenantId = :tenantId AND a.stage = :stage
        """)
    long countByStage(TenantId tenantId, String stage);
}
