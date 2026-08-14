package za.co.handyflow.platform.bookingagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookingagency.domain.model.BookAgencyPayment;

import java.util.List;
import java.util.UUID;

public interface BookAgencyPaymentRepository extends JpaRepository<BookAgencyPayment, UUID> {
    @Query("SELECT p FROM BookAgencyPayment p WHERE p.invoiceId = :invoiceId ORDER BY p.paidDate DESC")
    List<BookAgencyPayment> findByInvoice(@Param("invoiceId") UUID invoiceId);
}