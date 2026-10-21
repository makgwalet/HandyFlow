package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.Segment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SegmentRepository extends JpaRepository<Segment, UUID> {

    @Query("SELECT s FROM Segment s WHERE s.tenantId = :tenantId ORDER BY s.name ASC")
    List<Segment> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT s FROM Segment s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<Segment> findByIdAndTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
