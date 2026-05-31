package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ClinicPatientRepository extends JpaRepository<ClinicPatient, UUID> {

    @Query("""
        SELECT p FROM ClinicPatient p
        WHERE p.tenantId = :#{#tenantId.value} AND p.deletedAt IS NULL
        ORDER BY p.lastName, p.firstName
        """)
    Page<ClinicPatient> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT p FROM ClinicPatient p
        WHERE p.tenantId = :#{#tenantId.value} AND p.deletedAt IS NULL
        AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(p.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
             OR p.idNumber          LIKE CONCAT('%', :search, '%'))
        ORDER BY p.lastName, p.firstName
        """)
    Page<ClinicPatient> searchActive(TenantId tenantId, String search, Pageable pageable);

    @Query("SELECT p FROM ClinicPatient p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id AND p.deletedAt IS NULL")
    Optional<ClinicPatient> findActiveById(TenantId tenantId, UUID id);
}