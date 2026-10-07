package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultationEdit;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ClinicConsultationEditRepository extends JpaRepository<ClinicConsultationEdit, UUID> {

    @Query("SELECT e FROM ClinicConsultationEdit e WHERE e.tenantId = :#{#tenantId.value} AND e.consultationId = :consultationId ORDER BY e.editedAt DESC")
    List<ClinicConsultationEdit> findByConsultation(TenantId tenantId, UUID consultationId);
}
