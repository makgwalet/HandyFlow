// security/domain/repository/ShiftRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    @Query("SELECT s FROM Shift s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL ORDER BY s.startAt DESC")
    Page<Shift> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT s FROM Shift s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<Shift> findActiveById(TenantId tenantId, UUID id);

    // WHY? Detect overlapping shifts for the same guard
    @Query("""
        SELECT s FROM Shift s
        WHERE s.guardId = :guardId
        AND s.deletedAt IS NULL
        AND s.status NOT IN ('CANCELLED', 'MISSED')
        AND s.startAt < :endAt
        AND s.endAt   > :startAt
        """)
    List<Shift> findOverlapping(UUID guardId, Instant startAt, Instant endAt);

    @Query("SELECT s FROM Shift s WHERE s.tenantId = :tenantId AND s.siteId = :siteId AND s.deletedAt IS NULL ORDER BY s.startAt DESC")
    Page<Shift> findBySite(TenantId tenantId, UUID siteId, Pageable pageable);
}