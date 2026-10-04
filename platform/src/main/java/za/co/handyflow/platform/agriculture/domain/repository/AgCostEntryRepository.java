package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgCostEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgCostEntryRepository extends JpaRepository<AgCostEntry, UUID> {

    @Query("SELECT e FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.farmId = :farmId ORDER BY e.entryDate DESC, e.createdAt DESC")
    Page<AgCostEntry> findForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    @Query("SELECT e FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.targetType = :targetType AND e.targetId = :targetId ORDER BY e.entryDate DESC, e.createdAt DESC")
    Page<AgCostEntry> findForTarget(TenantId tenantId, String targetType, UUID targetId, Pageable pageable);

    @Query("SELECT e FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.allocationGroupId = :allocationGroupId ORDER BY e.createdAt ASC")
    List<AgCostEntry> findByAllocationGroup(TenantId tenantId, UUID allocationGroupId);

    @Query("SELECT e FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<AgCostEntry> findForTenantById(TenantId tenantId, UUID id);

    // Net cost (reversals included, so a corrected entry nets to zero). Rows are [category, amount].
    @Query("SELECT e.category, SUM(e.amount) FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.farmId = :farmId GROUP BY e.category")
    List<Object[]> sumByCategoryForFarm(TenantId tenantId, UUID farmId);

    @Query("SELECT e.category, SUM(e.amount) FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.targetType = :targetType AND e.targetId = :targetId GROUP BY e.category")
    List<Object[]> sumByCategoryForTarget(TenantId tenantId, String targetType, UUID targetId);

    // How many ACTIVE ledger entries of this source already point at this record (labour: at most one; the V308 unique index enforces it).
    @Query("SELECT COUNT(e) FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.sourceType = :sourceType AND e.sourceRef = :sourceRef AND e.status = 'ACTIVE'")
    long countActiveBySource(TenantId tenantId, String sourceType, UUID sourceRef);

    // Of these source records, the ones that already have an ACTIVE ledger entry of this source type: one query for a whole list (ADR-001 W4).
    @Query("SELECT DISTINCT e.sourceRef FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.sourceType = :sourceType AND e.sourceRef IN :refs AND e.status = 'ACTIVE'")
    List<UUID> findActiveSourceRefs(TenantId tenantId, String sourceType, Collection<UUID> refs);

    // Net cost (reversals included) of every target on a farm, by category, in one query (ADR-001 W5). Rows are [targetType, targetId, category, amount].
    @Query("SELECT e.targetType, e.targetId, e.category, SUM(e.amount) FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.farmId = :farmId GROUP BY e.targetType, e.targetId, e.category")
    List<Object[]> sumByTargetAndCategoryForFarm(TenantId tenantId, UUID farmId);
}
