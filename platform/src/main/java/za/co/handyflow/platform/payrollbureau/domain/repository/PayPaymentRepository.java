package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayPayment;

import java.util.List;
import java.util.UUID;

public interface PayPaymentRepository extends JpaRepository<PayPayment, UUID> {
    @Query("SELECT p FROM PayPayment p WHERE p.feeNoteId = :feeNoteId ORDER BY p.paidDate DESC")
    List<PayPayment> findByFeeNote(@Param("feeNoteId") UUID feeNoteId);
}