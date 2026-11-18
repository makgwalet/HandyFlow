package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.OtpVerification;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

// No custom methods needed — partyId IS the primary key, so JpaRepository's
// inherited findById(partyId)/save(...)/deleteById(partyId) already cover
// every operation OtpService needs.
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    /** Atomic increment, so simultaneous wrong guesses cannot overwrite each other's count. */
    @Modifying
    @Query("UPDATE OtpVerification v SET v.failedAttempts = v.failedAttempts + 1 WHERE v.partyId = :partyId")
    int incrementFailedAttempts(@Param("partyId") UUID partyId);
}
