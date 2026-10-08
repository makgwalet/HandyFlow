package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicLetterTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicLetterTemplateRepository extends JpaRepository<ClinicLetterTemplate, UUID> {

    @Query("SELECT x FROM ClinicLetterTemplate x WHERE x.tenantId = :#{#tenantId.value} AND x.archivedAt IS NULL AND (:kind IS NULL OR x.kind = :kind) ORDER BY x.kind, lower(x.name)")
    List<ClinicLetterTemplate> findLive(TenantId tenantId, String kind);

    @Query("SELECT x FROM ClinicLetterTemplate x WHERE x.tenantId = :#{#tenantId.value} AND x.id = :id")
    Optional<ClinicLetterTemplate> findOne(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(x) > 0 FROM ClinicLetterTemplate x WHERE x.tenantId = :#{#tenantId.value} AND x.archivedAt IS NULL AND x.kind = :kind AND lower(x.name) = lower(:name) AND (:exceptId IS NULL OR x.id <> :exceptId)")
    boolean nameTaken(TenantId tenantId, String kind, String name, UUID exceptId);
}
