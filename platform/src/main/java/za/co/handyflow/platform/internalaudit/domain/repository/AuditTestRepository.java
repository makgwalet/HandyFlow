package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditTestRepository extends JpaRepository<AuditTest, UUID> {

    @Query("SELECT t FROM AuditTest t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<AuditTest> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM AuditTest t WHERE t.tenantId = :tenantId AND t.sampleItemId = :sampleItemId")
    List<AuditTest> findBySampleItem(@Param("tenantId") UUID tenantId, @Param("sampleItemId") UUID sampleItemId);
}
