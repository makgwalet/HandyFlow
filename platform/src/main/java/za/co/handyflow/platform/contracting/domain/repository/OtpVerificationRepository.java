package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.contracting.domain.model.OtpVerification;

import java.util.UUID;

// No custom methods needed — partyId IS the primary key, so JpaRepository's
// inherited findById(partyId)/save(...)/deleteById(partyId) already cover
// every operation OtpService needs.
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
}
