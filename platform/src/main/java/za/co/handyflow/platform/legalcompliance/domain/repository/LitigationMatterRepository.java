package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatter;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LitigationMatterRepository extends JpaRepository<LitigationMatter, UUID> {

    @Query("""
        SELECT m FROM LitigationMatter m WHERE m.tenantId = :#{#tenantId.value}
        AND m.deletedAt IS NULL
        AND (:status IS NULL OR m.status = :status)
        ORDER BY m.openedDate DESC
        """)
    Page<LitigationMatter> findAllActive(TenantId tenantId, LitigationStatus status, Pageable pageable);

    @Query("SELECT m FROM LitigationMatter m WHERE m.tenantId = :#{#tenantId.value} AND m.id = :id AND m.deletedAt IS NULL")
    Optional<LitigationMatter> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(m) FROM LitigationMatter m WHERE m.tenantId = :#{#tenantId.value} AND m.deletedAt IS NULL")
    long countByTenant(TenantId tenantId);

    @Query("""
    SELECT m FROM LitigationMatter m WHERE m.tenantId = :#{#tenantId.value}
    AND m.deletedAt IS NULL
    AND m.closedDate IS NULL
    AND m.nextKeyDate IS NOT NULL
    AND m.nextKeyDate BETWEEN :from AND :to
    ORDER BY m.nextKeyDate ASC
    """)
    List<LitigationMatter> findWithKeyDateWithin(TenantId tenantId, LocalDate from, LocalDate to);
}
