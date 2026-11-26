package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayClientCharge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayClientChargeRepository extends JpaRepository<PayClientCharge, UUID> {

    @Query("SELECT c FROM PayClientCharge c WHERE c.tenantId = :tenantId AND c.payClientId = :clientId ORDER BY c.createdAt")
    List<PayClientCharge> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT c FROM PayClientCharge c WHERE c.tenantId = :tenantId AND c.payClientId = :clientId AND c.status = 'ACTIVE' ORDER BY c.createdAt")
    List<PayClientCharge> findActiveByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT c FROM PayClientCharge c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<PayClientCharge> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
