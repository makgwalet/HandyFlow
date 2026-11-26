package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayGroupRepository extends JpaRepository<PayGroup, UUID> {

    @Query("SELECT g FROM PayGroup g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<PayGroup> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM PayGroup g WHERE g.tenantId = :tenantId AND g.payClientId = :clientId ORDER BY g.status ASC, g.name ASC")
    List<PayGroup> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);
}
