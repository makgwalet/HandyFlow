package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientDocument;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicPatientDocumentRepository extends JpaRepository<ClinicPatientDocument, UUID> {

    @Query("SELECT d FROM ClinicPatientDocument d WHERE d.tenantId = :#{#tenantId.value} AND d.patientId = :patientId AND d.voidedAt IS NULL ORDER BY d.documentDate DESC, d.createdAt DESC")
    List<ClinicPatientDocument> findLive(TenantId tenantId, UUID patientId);

    @Query("SELECT d FROM ClinicPatientDocument d WHERE d.tenantId = :#{#tenantId.value} AND d.patientId = :patientId AND d.id = :id")
    Optional<ClinicPatientDocument> findOne(TenantId tenantId, UUID patientId, UUID id);
}
