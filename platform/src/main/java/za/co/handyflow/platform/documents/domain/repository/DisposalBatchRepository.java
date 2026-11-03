package za.co.handyflow.platform.documents.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.documents.domain.model.DisposalBatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisposalBatchRepository extends JpaRepository<DisposalBatch, UUID> {

    @Query("SELECT b FROM DisposalBatch b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<DisposalBatch> findOwned(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Locked, so two people deciding the same request at once are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM DisposalBatch b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<DisposalBatch> findOwnedForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT b FROM DisposalBatch b WHERE b.tenantId = :tenantId ORDER BY b.proposedAt DESC")
    List<DisposalBatch> findForTenant(@Param("tenantId") UUID tenantId, Pageable page);

    @Query("SELECT COUNT(b) FROM DisposalBatch b WHERE b.tenantId = :tenantId AND b.status IN ('PROPOSED', 'APPROVED')")
    long countOpen(@Param("tenantId") UUID tenantId);
}
