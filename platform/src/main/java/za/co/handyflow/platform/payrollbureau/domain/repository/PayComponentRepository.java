package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayComponent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayComponentRepository extends JpaRepository<PayComponent, UUID> {

    @Query("SELECT c FROM PayComponent c WHERE c.tenantId = :tenantId ORDER BY c.role, c.name")
    List<PayComponent> findByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM PayComponent c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<PayComponent> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM PayComponent c WHERE c.tenantId = :tenantId AND c.id IN :ids")
    List<PayComponent> findByTenantAndIds(@Param("tenantId") UUID tenantId, @Param("ids") java.util.Collection<UUID> ids);

    @Query("SELECT COUNT(c) > 0 FROM PayComponent c WHERE c.tenantId = :tenantId AND UPPER(c.code) = UPPER(:code)")
    boolean existsByCode(@Param("tenantId") UUID tenantId, @Param("code") String code);
}
