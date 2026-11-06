package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseAllocationEvent;

import java.util.List;
import java.util.UUID;

public interface WhseAllocationEventRepository extends JpaRepository<WhseAllocationEvent, UUID> {

    @Query("SELECT e FROM WhseAllocationEvent e WHERE e.tenantId = :tenantId AND e.referenceType = :referenceType AND e.referenceId = :referenceId ORDER BY e.createdAt ASC")
    List<WhseAllocationEvent> findByReference(@Param("tenantId") UUID tenantId, @Param("referenceType") String referenceType,
                                              @Param("referenceId") UUID referenceId);
}
