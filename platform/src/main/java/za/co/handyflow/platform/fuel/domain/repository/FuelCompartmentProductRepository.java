package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelCompartmentProduct;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FuelCompartmentProductRepository extends JpaRepository<FuelCompartmentProduct, UUID> {

    @Query("SELECT p FROM FuelCompartmentProduct p WHERE p.tenantId = :tenantId AND p.compartmentId IN :ids")
    List<FuelCompartmentProduct> findForCompartments(TenantId tenantId, Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FuelCompartmentProduct p WHERE p.tenantId = :tenantId AND p.compartmentId = :compartmentId")
    void deleteForCompartment(TenantId tenantId, UUID compartmentId);
}
