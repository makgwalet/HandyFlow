package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditUniverseEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditUniverseEntryRepository extends JpaRepository<AuditUniverseEntry, UUID> {

    @Query("SELECT e FROM AuditUniverseEntry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<AuditUniverseEntry> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT e FROM AuditUniverseEntry e WHERE e.tenantId = :tenantId AND e.active = true ORDER BY e.name")
    List<AuditUniverseEntry> findAllActive(@Param("tenantId") UUID tenantId);
}
