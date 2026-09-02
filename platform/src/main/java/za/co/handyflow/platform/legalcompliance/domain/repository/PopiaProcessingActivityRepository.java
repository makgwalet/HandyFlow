package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.PopiaProcessingActivity;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PopiaProcessingActivityRepository extends JpaRepository<PopiaProcessingActivity, UUID> {

    // FIX: see RegulatoryObligationRepository's comment — same
    // embedded-TenantId vs SpEL-unwrap type mismatch, same fix.
    @Query("""
        SELECT a FROM PopiaProcessingActivity a WHERE a.tenantId = :tenantId
        AND a.deletedAt IS NULL
        ORDER BY a.dataCategory, a.activityName
        """)
    List<PopiaProcessingActivity> findAllActive(TenantId tenantId);

    @Query("SELECT a FROM PopiaProcessingActivity a WHERE a.tenantId = :tenantId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<PopiaProcessingActivity> findActiveById(TenantId tenantId, UUID id);
}