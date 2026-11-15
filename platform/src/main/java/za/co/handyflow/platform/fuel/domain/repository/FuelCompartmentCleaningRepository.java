package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelCompartmentCleaning;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelCompartmentCleaningRepository extends JpaRepository<FuelCompartmentCleaning, UUID> {

    /** Newest first. */
    @Query("SELECT c FROM FuelCompartmentCleaning c WHERE c.tenantId = :tenantId AND c.compartmentId = :compartmentId ORDER BY c.cleanedAt DESC, c.createdAt DESC")
    List<FuelCompartmentCleaning> findForCompartment(TenantId tenantId, UUID compartmentId, Pageable page);
}
