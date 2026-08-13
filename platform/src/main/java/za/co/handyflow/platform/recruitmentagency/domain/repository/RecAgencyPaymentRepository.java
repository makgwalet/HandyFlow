package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyPayment;

import java.util.List;
import java.util.UUID;

public interface RecAgencyPaymentRepository extends JpaRepository<RecAgencyPayment, UUID> {
    @Query("SELECT p FROM RecAgencyPayment p WHERE p.invoiceId = :invoiceId ORDER BY p.paidDate DESC")
    List<RecAgencyPayment> findByInvoice(@Param("invoiceId") UUID invoiceId);
}