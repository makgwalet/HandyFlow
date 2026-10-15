package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReviewNote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditReviewNoteRepository extends JpaRepository<AuditReviewNote, UUID> {

    @Query("SELECT n FROM AuditReviewNote n WHERE n.tenantId = :tenantId AND n.id = :id")
    Optional<AuditReviewNote> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT n FROM AuditReviewNote n WHERE n.tenantId = :tenantId AND n.workpaperId = :workpaperId ORDER BY n.raisedAt")
    List<AuditReviewNote> findByWorkpaper(@Param("tenantId") UUID tenantId, @Param("workpaperId") UUID workpaperId);

    @Query("SELECT n FROM AuditReviewNote n WHERE n.tenantId = :tenantId AND n.engagementId = :engagementId AND n.status <> 'CLEARED' ORDER BY n.raisedAt")
    List<AuditReviewNote> findUnclearedByEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);

    @Query("SELECT n FROM AuditReviewNote n WHERE n.tenantId = :tenantId")
    List<AuditReviewNote> findAllForTenant(@Param("tenantId") UUID tenantId);
}
