package za.co.handyflow.platform.shared;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RateLimitEntryRepository extends JpaRepository<RateLimitEntry, String> {

    // Same reasoning as OtpRateLimitRepository.findByPartyIdForUpdate() —
    // a plain findById()+save() would not be atomic across two concurrent
    // requests hitting the same key, allowing more requests through than
    // the configured limit. This pessimistic lock makes the
    // read-modify-write atomic across instances via the database.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RateLimitEntry r WHERE r.rateKey = :rateKey")
    Optional<RateLimitEntry> findByKeyForUpdate(String rateKey);
}