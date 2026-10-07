package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultationAddendum;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ClinicConsultationAddendumRepository extends JpaRepository<ClinicConsultationAddendum, UUID> {

    @Query("SELECT a FROM ClinicConsultationAddendum a WHERE a.tenantId = :#{#tenantId.value} AND a.consultationId = :consultationId ORDER BY a.createdAt ASC")
    List<ClinicConsultationAddendum> findByConsultation(TenantId tenantId, UUID consultationId);
}
