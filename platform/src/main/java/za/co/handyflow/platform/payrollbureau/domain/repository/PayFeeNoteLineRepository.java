package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayFeeNoteLine;

import java.util.List;
import java.util.UUID;

public interface PayFeeNoteLineRepository extends JpaRepository<PayFeeNoteLine, UUID> {
    @Query("SELECT l FROM PayFeeNoteLine l WHERE l.feeNoteId = :feeNoteId")
    List<PayFeeNoteLine> findByFeeNote(@Param("feeNoteId") UUID feeNoteId);
}