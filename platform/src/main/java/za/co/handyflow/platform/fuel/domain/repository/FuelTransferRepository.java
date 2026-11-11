package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTransferRepository extends JpaRepository<FuelTransfer, UUID> {

    @Query("SELECT t FROM FuelTransfer t WHERE t.tenantId = :tenantId ORDER BY t.createdAt DESC")
    Page<FuelTransfer> findAllForTenant(TenantId tenantId, Pageable pageable);
}
