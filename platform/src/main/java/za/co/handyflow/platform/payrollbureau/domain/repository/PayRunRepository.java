package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;

import java.util.Optional;
import java.util.UUID;

public interface PayRunRepository extends JpaRepository<PayRun, UUID> {

    @Query("SELECT r FROM PayRun r WHERE r.payClientId = :payClientId ORDER BY r.periodStart DESC")
    Page<PayRun> findByClient(@Param("payClientId") UUID payClientId, Pageable pageable);

    @Query("SELECT r FROM PayRun r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<PayRun> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}