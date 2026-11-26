package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSamplingRuleVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSamplingRuleVersionRepository extends JpaRepository<TrainProvSamplingRuleVersion, UUID> {

    @Query("SELECT v FROM TrainProvSamplingRuleVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.ruleId = :ruleId ORDER BY v.versionNo DESC")
    List<TrainProvSamplingRuleVersion> forRule(TenantId tenantId, UUID ruleId);

    @Query("SELECT v FROM TrainProvSamplingRuleVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.id = :id")
    Optional<TrainProvSamplingRuleVersion> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT v FROM TrainProvSamplingRuleVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.ruleId IN :ruleIds ORDER BY v.versionNo DESC")
    List<TrainProvSamplingRuleVersion> forRules(TenantId tenantId, java.util.Collection<UUID> ruleIds);

    /** The version in force on a day: the latest one that started on or before it. */
    @Query("""
        SELECT v FROM TrainProvSamplingRuleVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.ruleId = :ruleId AND v.effectiveFrom <= :day
        ORDER BY v.effectiveFrom DESC, v.versionNo DESC
        """)
    List<TrainProvSamplingRuleVersion> inForceOn(TenantId tenantId, UUID ruleId, LocalDate day);
}
