package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalAccessLog;

import java.util.List;
import java.util.UUID;

public interface ProjectTransmittalAccessLogRepository extends JpaRepository<ProjectTransmittalAccessLog, UUID> {

    @Query("""
            SELECT l FROM ProjectTransmittalAccessLog l
            WHERE l.tenantId = :tenantId AND l.transmittalId = :transmittalId
            ORDER BY l.occurredAt DESC
            """)
    List<ProjectTransmittalAccessLog> findByTransmittal(@Param("tenantId") UUID tenantId,
                                                        @Param("transmittalId") UUID transmittalId);
}
