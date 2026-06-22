package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
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

    // WHY? N+1 fix — batch-load multiple patients in one query instead of one per row
    @Query("SELECT p FROM ClinicPatient p WHERE p.tenantId = :#{#tenantId.value} AND p.id IN :ids AND p.deletedAt IS NULL")
    List<ClinicPatient> findAllByIds(TenantId tenantId, @Param("ids") Collection<UUID> ids);


    /** Find a patient by tenant + ID — used by patchPatient and getFamilyMembers */
    @Query("SELECT p FROM ClinicPatient p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<ClinicPatient> findByTenantIdAndId(
            @Param("tenantId") TenantId tenantId,
            @Param("id") UUID id);

    /** All active patients for a tenant (excludes archived) */
    @Query("SELECT p FROM ClinicPatient p WHERE p.tenantId = :#{#tenantId.value} AND p.archivedAt IS NULL ORDER BY p.lastName, p.firstName")
    Page<ClinicPatient> findActiveByTenantId(
            @Param("tenantId") TenantId tenantId,
            Pageable pageable);

    /** All patients including archived */
    @Query("SELECT p FROM ClinicPatient p WHERE p.tenantId = :#{#tenantId.value} ORDER BY p.lastName, p.firstName")
    Page<ClinicPatient> findByTenantId(
            @Param("tenantId") TenantId tenantId,
            Pageable pageable);

    /** Full-text search across name, ID number, phone — excludes archived */
    @Query("""
        SELECT p FROM ClinicPatient p
        WHERE p.tenantId = :#{#tenantId.value}
          AND p.archivedAt IS NULL
          AND (LOWER(p.fullName)  LIKE LOWER(CONCAT('%',:q,'%'))
            OR p.idNumber         LIKE CONCAT('%',:q,'%')
            OR p.phone            LIKE CONCAT('%',:q,'%'))
        ORDER BY p.lastName, p.firstName
        """)
    Page<ClinicPatient> search(
            @Param("tenantId") TenantId tenantId,
            @Param("q") String query,
            Pageable pageable);

    /** Same search including archived records */
    @Query("""
        SELECT p FROM ClinicPatient p
        WHERE p.tenantId = :#{#tenantId.value}
          AND (LOWER(p.fullName)  LIKE LOWER(CONCAT('%',:q,'%'))
            OR p.idNumber         LIKE CONCAT('%',:q,'%')
            OR p.phone            LIKE CONCAT('%',:q,'%'))
        ORDER BY p.lastName, p.firstName
        """)
    Page<ClinicPatient> searchIncludingArchived(
            @Param("tenantId") TenantId tenantId,
            @Param("q") String query,
            Pageable pageable);

    /** All dependants of a principal — for family tab and patient list filter */
    @Query("""
        SELECT p FROM ClinicPatient p
        WHERE p.tenantId = :#{#tenantId.value}
          AND p.principalId = :principalId
        ORDER BY p.relationship, p.lastName
        """)
    Page<ClinicPatient> findByTenantIdAndPrincipalId(
            @Param("tenantId") TenantId tenantId,
            @Param("principalId") UUID principalId,
            Pageable pageable);

    /** List version of above — used by getFamilyMembers */
    @Query("""
        SELECT p FROM ClinicPatient p
        WHERE p.tenantId = :#{#tenantId.value}
          AND p.principalId = :principalId
        ORDER BY p.relationship, p.lastName
        """)
    List<ClinicPatient> findDependantsByPrincipalId(
            @Param("tenantId") TenantId tenantId,
            @Param("principalId") UUID principalId);
}