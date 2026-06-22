package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicPractitionerRepository extends JpaRepository<ClinicPractitioner, UUID> {

    @Query("SELECT p FROM ClinicPractitioner p WHERE p.tenantId = :#{#tenantId.value} AND p.deletedAt IS NULL ORDER BY p.lastName")
    Page<ClinicPractitioner> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT p FROM ClinicPractitioner p WHERE p.tenantId = :#{#tenantId.value} AND p.active = true AND p.deletedAt IS NULL ORDER BY p.lastName")
    List<ClinicPractitioner> findAllActiveList(TenantId tenantId);

    @Query("SELECT p FROM ClinicPractitioner p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id AND p.deletedAt IS NULL")
    Optional<ClinicPractitioner> findActiveById(TenantId tenantId, UUID id);

    // WHY? N+1 fix — same pattern as ClinicPatientRepository
    @Query("SELECT p FROM ClinicPractitioner p WHERE p.tenantId = :#{#tenantId.value} AND p.id IN :ids AND p.deletedAt IS NULL")
    List<ClinicPractitioner> findAllByIds(TenantId tenantId, @Param("ids") Collection<UUID> ids);
}