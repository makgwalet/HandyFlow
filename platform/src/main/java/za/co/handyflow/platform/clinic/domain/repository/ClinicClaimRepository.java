package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Patch 0178: one page of the claims screen. Ordered newest first; the id breaks ties so pages never repeat a row. */
    @Query(value = "SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} ORDER BY c.createdAt DESC, c.id",
           countQuery = "SELECT COUNT(c) FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value}")
    Page<ClinicClaim> findPage(TenantId tenantId, Pageable pageable);

    @Query(value = "SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.status = :status ORDER BY c.createdAt DESC, c.id",
           countQuery = "SELECT COUNT(c) FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.status = :status")
    Page<ClinicClaim> findPageByStatus(TenantId tenantId, String status, Pageable pageable);

    /** Patch 0178: id, status and scheme portion only, for the summary figures (no lines loaded). */
    @Query("SELECT c.id, c.status, c.schemePortion FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value}")
    List<Object[]> summaryFacts(TenantId tenantId);

    @Query("SELECT c.id, c.status, c.schemePortion FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.status = :status")
    List<Object[]> summaryFactsByStatus(TenantId tenantId, String status);

    /** FIX: needed for the patient statement of account — no query filtered claims by patient at all. */
    @Query("SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.patientId = :patientId ORDER BY c.createdAt DESC")
    List<ClinicClaim> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.consultationId = :consultationId AND c.status <> 'VOIDED'")
    Optional<ClinicClaim> findByConsultation(TenantId tenantId, UUID consultationId);

    @Query("SELECT c FROM ClinicClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<ClinicClaim> findActiveById(TenantId tenantId, UUID id);
}