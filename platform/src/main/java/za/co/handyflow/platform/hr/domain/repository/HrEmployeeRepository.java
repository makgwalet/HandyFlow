package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeRepository extends JpaRepository<HrEmployee, UUID> {

    @Query("""
    SELECT e FROM HrEmployee e
    WHERE e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL
    AND (:status IS NULL OR e.status = :status)
    AND (CAST(:search AS string) IS NULL
         OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
         OR LOWER(e.lastName)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
         OR LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
         OR e.employeeNumber   LIKE CONCAT('%', CAST(:search AS string), '%'))
    ORDER BY e.lastName, e.firstName
    """)
    Page<HrEmployee> findAllActive(TenantId tenantId, String status,
                                   String search, Pageable pageable);

    @Query("SELECT e FROM HrEmployee e WHERE e.tenantId = :#{#tenantId.value} AND e.status = 'ACTIVE' AND e.deletedAt IS NULL ORDER BY e.lastName")
    List<HrEmployee> findAllActiveList(TenantId tenantId);

    @Query("SELECT e FROM HrEmployee e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id AND e.deletedAt IS NULL")
    Optional<HrEmployee> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(e) FROM HrEmployee e WHERE e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL")
    long countByTenant(TenantId tenantId);

    /** Other live employees with the same ID number. Used to refuse duplicates. */
    @Query("SELECT COUNT(e) FROM HrEmployee e WHERE e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL AND e.idNumber = :idNumber AND (:excludeId IS NULL OR e.id <> :excludeId)")
    long countByIdNumber(TenantId tenantId, String idNumber, UUID excludeId);

    @Query("SELECT e FROM HrEmployee e WHERE e.tenantId = :#{#tenantId.value} AND e.userId = :userId AND e.deletedAt IS NULL")
    Optional<HrEmployee> findByUserId(TenantId tenantId, UUID userId);

    /** Everyone not yet terminated, for building the reporting lines. */
    @Query("SELECT e FROM HrEmployee e WHERE e.tenantId = :#{#tenantId.value} AND e.status <> 'TERMINATED' AND e.deletedAt IS NULL")
    List<HrEmployee> findNotTerminated(TenantId tenantId);
}
