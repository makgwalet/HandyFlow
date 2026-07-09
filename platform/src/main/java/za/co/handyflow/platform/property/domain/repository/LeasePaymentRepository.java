// property/domain/repository/LeasePaymentRepository.java

package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.property.domain.model.LeasePayment;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeasePaymentRepository extends JpaRepository<LeasePayment, UUID> {

    @Query("SELECT p FROM LeasePayment p WHERE p.leaseId = :leaseId ORDER BY p.periodYear DESC, p.periodMonth DESC")
    Page<LeasePayment> findByLease(UUID leaseId, Pageable pageable);

    @Query("SELECT p FROM LeasePayment p WHERE p.leaseId = :leaseId AND p.periodYear = :year AND p.periodMonth = :month")
    Optional<LeasePayment> findByPeriod(UUID leaseId, int year, int month);

    @Query("SELECT p FROM LeasePayment p WHERE p.tenantId = :tenantId AND p.status IN ('PENDING','OVERDUE','PARTIAL') ORDER BY p.dueDate ASC")
    List<LeasePayment> findOutstanding(TenantId tenantId);

    // NEW: backs PropertyScheduler's overdue-marking job. LeasePayment.
    // markOverdue() already existed and was already correctly implemented
    // — confirmed by grep that its only match anywhere in the codebase was
    // its own definition, zero call sites. The domain logic was built and
    // simply never wired to anything that would actually run it, so a
    // payment past its due date just sat as PENDING forever unless someone
    // happened to record a payment against it later.
    //
    // Deliberately cross-tenant (no tenant_id filter) and returns real
    // LeasePayment entities directly, same reasoning as
    // LeaseRepository.findAllActiveExpiringBy — each entity already
    // carries its own correctly-typed TenantId via its embedded field, so
    // there's no need to reconstruct one from a raw UUID afterward.
    @Query("""
            SELECT p FROM LeasePayment p
            WHERE p.status IN ('PENDING','PARTIAL') AND p.dueDate < :today
            """)
    List<LeasePayment> findAllPastDueUnmarked(LocalDate today);
}