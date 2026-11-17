package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelCompartmentCleaning;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelCompartmentCleaningRepository extends JpaRepository<FuelCompartmentCleaning, UUID> {

    @Query("SELECT c FROM FuelCompartmentCleaning c WHERE c.tenantId = :tenantId AND c.id = :id")
    java.util.Optional<FuelCompartmentCleaning> findOne(TenantId tenantId, UUID id);

    /** Only cleanings whose certificate is uploaded and verified, newest first. Used when the company asks for verified evidence. */
    @Query("SELECT c FROM FuelCompartmentCleaning c WHERE c.tenantId = :tenantId AND c.compartmentId = :compartmentId AND c.fileRef IS NOT NULL AND c.verifiedAt IS NOT NULL ORDER BY c.cleanedAt DESC, c.createdAt DESC")
    List<FuelCompartmentCleaning> findVerifiedForCompartment(TenantId tenantId, UUID compartmentId, Pageable page);

    /** Newest first. */
    @Query("SELECT c FROM FuelCompartmentCleaning c WHERE c.tenantId = :tenantId AND c.compartmentId = :compartmentId ORDER BY c.cleanedAt DESC, c.createdAt DESC")
    List<FuelCompartmentCleaning> findForCompartment(TenantId tenantId, UUID compartmentId, Pageable page);
}
