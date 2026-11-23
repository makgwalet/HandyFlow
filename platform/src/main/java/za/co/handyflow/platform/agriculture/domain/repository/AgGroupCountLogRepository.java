package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgGroupCountLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface AgGroupCountLogRepository extends JpaRepository<AgGroupCountLog, UUID> {

    @Query("SELECT l FROM AgGroupCountLog l WHERE l.tenantId = :tenantId AND l.groupId = :groupId ORDER BY l.createdAt DESC")
    Page<AgGroupCountLog> findByGroup(TenantId tenantId, UUID groupId, Pageable pageable);
}
