package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustSettingChange;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpTrustSettingChangeRepository extends JpaRepository<LpTrustSettingChange, UUID> {

    @Query("SELECT c FROM LpTrustSettingChange c WHERE c.tenantId = :tenantId ORDER BY c.changedAt DESC")
    List<LpTrustSettingChange> latest(TenantId tenantId, Pageable pageable);

    /** The setting in force: the most recent change, or none (strict default). */
    default Optional<LpTrustSettingChange> current(TenantId tenantId) {
        return latest(tenantId, PageRequest.of(0, 1)).stream().findFirst();
    }
}
