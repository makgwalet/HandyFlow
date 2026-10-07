package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultationTransition;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ClinicConsultationTransitionRepository extends JpaRepository<ClinicConsultationTransition, UUID> {

    @Query("SELECT t FROM ClinicConsultationTransition t WHERE t.tenantId = :#{#tenantId.value} AND t.consultationId = :consultationId ORDER BY t.createdAt ASC")
    List<ClinicConsultationTransition> findByConsultation(TenantId tenantId, UUID consultationId);
}
