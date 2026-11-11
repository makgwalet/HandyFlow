package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTankerCompartment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelTankerCompartmentRepository extends JpaRepository<FuelTankerCompartment, UUID> {

    @Query("SELECT c FROM FuelTankerCompartment c WHERE c.tenantId = :tenantId AND c.tankerId = :tankerId ORDER BY c.number")
    List<FuelTankerCompartment> findByTanker(TenantId tenantId, UUID tankerId);

    @Query("SELECT c FROM FuelTankerCompartment c WHERE c.tenantId = :tenantId AND c.tankerId IN :tankerIds ORDER BY c.number")
    List<FuelTankerCompartment> findByTankers(TenantId tenantId, java.util.Collection<UUID> tankerIds);
}
