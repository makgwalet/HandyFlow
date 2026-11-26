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

    /** How many times a charge has been billed on a client's fee notes (not cancelled) with an invoice date in the range. */
    @Query("""
        SELECT COUNT(l) FROM PayFeeNoteLine l, PayFeeNote f
        WHERE l.feeNoteId = f.id AND f.tenantId = :tenantId AND f.payClientId = :clientId AND l.chargeId = :chargeId
          AND f.status <> 'CANCELLED' AND f.invoiceDate >= :from AND f.invoiceDate <= :to
    """)
    long countChargeBilled(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, @Param("chargeId") UUID chargeId,
                           @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}
