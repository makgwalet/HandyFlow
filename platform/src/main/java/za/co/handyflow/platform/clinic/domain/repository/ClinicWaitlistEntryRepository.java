package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicWaitlistEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicWaitlistEntryRepository extends JpaRepository<ClinicWaitlistEntry, UUID> {

    @Query("SELECT w FROM ClinicWaitlistEntry w WHERE w.tenantId = :#{#tenantId.value} AND w.status = 'WAITING' ORDER BY w.createdAt ASC")
    List<ClinicWaitlistEntry> findActive(TenantId tenantId);

    @Query("SELECT w FROM ClinicWaitlistEntry w WHERE w.tenantId = :#{#tenantId.value} AND w.id = :id")
    Optional<ClinicWaitlistEntry> findByIdAndTenant(TenantId tenantId, UUID id);
}