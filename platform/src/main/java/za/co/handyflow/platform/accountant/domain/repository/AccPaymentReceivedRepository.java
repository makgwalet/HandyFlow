package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccPaymentReceived;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccPaymentReceivedRepository extends JpaRepository<AccPaymentReceived, UUID> {

    /** Payment history for a fee note, most recent first. */
    @Query("""
        SELECT p FROM AccountantPaymentReceived p
        WHERE p.feeNoteId = :feeNoteId
        ORDER BY p.paymentDate DESC, p.createdAt DESC
    """)
    List<AccPaymentReceived> findByFeeNoteId(@Param("feeNoteId") UUID feeNoteId);

    /**
     * Cumulative amount paid against a fee note. Uses COALESCE so this
     * returns ZERO for a fee note with no payments yet, not null —
     * TimeEntryRepository.sumWipByClient() in this same module returns
     * null in the equivalent no-rows case and every caller has to
     * remember to guard against it; this avoids repeating that footgun.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM AccountantPaymentReceived p WHERE p.feeNoteId = :feeNoteId")
    BigDecimal sumByFeeNoteId(@Param("feeNoteId") UUID feeNoteId);
}