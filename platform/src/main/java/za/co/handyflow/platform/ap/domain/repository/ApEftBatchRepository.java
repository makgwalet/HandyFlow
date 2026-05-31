package za.co.handyflow.platform.ap.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.ap.domain.model.ApEftBatch;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ApEftBatchRepository extends JpaRepository<ApEftBatch, UUID> {

    @Query("""
        SELECT b FROM ApEftBatch b
        WHERE b.tenantId = :tenantId
        ORDER BY b.createdAt DESC
        """)
    Page<ApEftBatch> findAll(TenantId tenantId, Pageable pageable);

    Optional<ApEftBatch> findByIdAndTenantId(UUID id, TenantId tenantId);

    // WHY COALESCE? Returns 0 if no batches exist yet for this tenant.
    @Query("""
        SELECT COALESCE(MAX(CAST(SUBSTRING(b.batchNumber, 5) AS int)), 0)
        FROM ApEftBatch b
        WHERE b.tenantId = :tenantId
        """)
    int findMaxBatchSequence(TenantId tenantId);
}
