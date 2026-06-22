package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicClaimRepository extends JpaRepository<ClinicClaim, UUID> {

    @Query("SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} ORDER BY c.createdAt DESC")
    List<ClinicClaim> findAll(TenantId tenantId);

    @Query("SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.status = :status ORDER BY c.createdAt DESC")
    List<ClinicClaim> findByStatus(TenantId tenantId, String status);

    @Query("SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.consultationId = :consultationId")
    Optional<ClinicClaim> findByConsultation(TenantId tenantId, UUID consultationId);

    @Query("SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<ClinicClaim> findActiveById(TenantId tenantId, UUID id);
}
