package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicObservation;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicObservationRepository extends JpaRepository<ClinicObservation, UUID> {

    @Query("SELECT o FROM ClinicObservation o WHERE o.tenantId = :#{#tenantId.value} AND o.patientId = :patientId ORDER BY o.takenAt DESC")
    List<ClinicObservation> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT o FROM ClinicObservation o WHERE o.tenantId = :#{#tenantId.value} AND o.patientId = :patientId AND o.id = :id")
    Optional<ClinicObservation> findOne(TenantId tenantId, UUID patientId, UUID id);

    /** Derived rows written from a consultation's vitals are rebuilt whenever the consultation changes. */
    @Modifying
    @Query("DELETE FROM ClinicObservation o WHERE o.tenantId = :#{#tenantId.value} AND o.consultationId = :consultationId AND o.source = 'CONSULTATION'")
    void deleteConsultationDerived(TenantId tenantId, UUID consultationId);
}
