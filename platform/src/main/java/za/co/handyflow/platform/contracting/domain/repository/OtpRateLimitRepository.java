package za.co.handyflow.platform.contracting.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.OtpRateLimit;

import java.util.Optional;
import java.util.UUID;

public interface OtpRateLimitRepository extends JpaRepository<OtpRateLimit, UUID> {

    // FIX: the original ConcurrentHashMap.compute(...) calls were atomic
    // read-modify-write operations in memory — a plain findById() + save()
    // here would NOT be atomic across two concurrent requests for the same
    // party (both could read requestCount=2, both proceed, both increment to
    // 3, silently allowing 4 requests through a 3-request limit). This
    // pessimistic lock (SELECT ... FOR UPDATE under the hood) makes the same
    // read-modify-write atomic again, this time across instances via the
    // database instead of within one JVM's memory. Contention risk here is
    // negligible — this locks one row, for the duration of one fast
    // read-then-write, and a single party sending genuinely concurrent OTP
    // requests from two places at once is not a real scenario this needs to
    // optimize for.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM OtpRateLimit r WHERE r.partyId = :partyId")
    Optional<OtpRateLimit> findByPartyIdForUpdate(UUID partyId);
}
