// property/domain/repository/LeasePaymentRepository.java

package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.property.domain.model.LeasePayment;
import za.co.handyflow.platform.shared.TenantId;

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
}