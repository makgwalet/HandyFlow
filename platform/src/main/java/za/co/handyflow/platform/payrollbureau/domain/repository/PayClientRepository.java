package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayClient;

import java.util.Optional;
import java.util.UUID;

public interface PayClientRepository extends JpaRepository<PayClient, UUID> {

    @Query("""
        SELECT c FROM PayClient c
        WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    Page<PayClient> findAllActive(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("""
        SELECT c FROM PayClient c
        WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL
    """)
    Optional<PayClient> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}