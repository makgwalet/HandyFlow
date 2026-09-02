package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPaymentPlan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollAgencyPaymentPlanRepository extends JpaRepository<CollAgencyPaymentPlan, UUID> {

    @Query("SELECT p FROM CollAgencyPaymentPlan p WHERE p.tenantId = :tenantId AND p.debtorAccountId = :debtorAccountId ORDER BY p.createdAt DESC")
    List<CollAgencyPaymentPlan> findByDebtorAccount(@Param("tenantId") UUID tenantId, @Param("debtorAccountId") UUID debtorAccountId);

    @Query("SELECT p FROM CollAgencyPaymentPlan p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<CollAgencyPaymentPlan> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM CollAgencyPaymentPlan p WHERE p.status = 'ACTIVE' AND p.nextDueDate BETWEEN :from AND :to")
    List<CollAgencyPaymentPlan> findActiveWithInstallmentDueWithinAcrossTenants(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT p FROM CollAgencyPaymentPlan p WHERE p.status = 'ACTIVE' AND p.nextDueDate < :today")
    List<CollAgencyPaymentPlan> findActiveOverdueAcrossTenants(@Param("today") LocalDate today);
}
