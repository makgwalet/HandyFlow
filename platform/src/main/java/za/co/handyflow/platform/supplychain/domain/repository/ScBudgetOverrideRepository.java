package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScBudgetOverride;

import java.math.BigDecimal;
import java.util.UUID;

public interface ScBudgetOverrideRepository extends JpaRepository<ScBudgetOverride, UUID> {

    /** Is there an override for this entity that covers at least `amount`? */
    @Query("""
            SELECT COUNT(o) FROM ScBudgetOverride o
            WHERE o.tenantId = :tenantId AND o.entityType = :entityType AND o.entityId = :entityId AND o.amount >= :amount
            """)
    long countCovering(@Param("tenantId") UUID tenantId, @Param("entityType") String entityType,
                       @Param("entityId") UUID entityId, @Param("amount") BigDecimal amount);
}
