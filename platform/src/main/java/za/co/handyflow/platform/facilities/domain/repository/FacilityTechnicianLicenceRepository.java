package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnicianLicence;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityTechnicianLicenceRepository extends JpaRepository<FacilityTechnicianLicence, UUID> {

    @Query("SELECT l FROM FacilityTechnicianLicence l WHERE l.tenantId.value = :#{#tenantId.value} AND l.id = :id")
    Optional<FacilityTechnicianLicence> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT l FROM FacilityTechnicianLicence l WHERE l.tenantId.value = :#{#tenantId.value} AND l.technicianId = :technicianId " +
            "ORDER BY l.licenceType ASC, l.expiryDate DESC")
    List<FacilityTechnicianLicence> findForTechnician(@Param("tenantId") TenantId tenantId, @Param("technicianId") UUID technicianId);

    /** VALID licences of active technicians that have lapsed or expire by {@code cutoff}: the attention list. */
    @Query("SELECT l FROM FacilityTechnicianLicence l WHERE l.tenantId.value = :#{#tenantId.value} AND l.status = 'VALID' " +
            "AND l.expiryDate IS NOT NULL AND l.expiryDate <= :cutoff AND l.technicianId IN " +
            "(SELECT t.id FROM FacilityTechnician t WHERE t.tenantId.value = :#{#tenantId.value} AND t.active = true AND t.deletedAt IS NULL) " +
            "ORDER BY l.expiryDate ASC")
    List<FacilityTechnicianLicence> findLapsedOrExpiringForActiveTechnicians(@Param("tenantId") TenantId tenantId, @Param("cutoff") LocalDate cutoff);

    /** Cross-tenant sweep for the daily reminders. */
    @Query("SELECT l FROM FacilityTechnicianLicence l WHERE l.status = 'VALID' AND l.expiryDate IS NOT NULL AND l.expiryDate <= :cutoff " +
            "AND l.technicianId IN (SELECT t.id FROM FacilityTechnician t WHERE t.active = true AND t.deletedAt IS NULL)")
    List<FacilityTechnicianLicence> findValidExpiringAcrossTenants(@Param("cutoff") LocalDate cutoff);
}
