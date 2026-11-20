package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeCharterRules;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CommitteeCharterRulesRepository extends JpaRepository<CommitteeCharterRules, UUID> {

    /** Every version, newest first. */
    @Query("SELECT x FROM CommitteeCharterRules x WHERE x.tenantId = :tenantId AND x.committeeId = :committeeId ORDER BY x.versionNo DESC")
    List<CommitteeCharterRules> history(TenantId tenantId, UUID committeeId);

    @Query("SELECT COALESCE(MAX(x.versionNo), 0) FROM CommitteeCharterRules x WHERE x.tenantId = :tenantId AND x.committeeId = :committeeId")
    int latestVersion(TenantId tenantId, UUID committeeId);
}
