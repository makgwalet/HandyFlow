package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.OtpVerification;
import za.co.handyflow.platform.contracting.domain.repository.OtpVerificationRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class OtpAttemptTrackerTest {

    private final OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
    private final OtpAttemptTracker tracker = new OtpAttemptTracker(repo);
    private final UUID party = UUID.randomUUID();

    private OtpVerification withAttempts(int n) throws Exception {
        OtpVerification v = OtpVerification.create(party, "hash", Instant.now().plusSeconds(600));
        var f = OtpVerification.class.getDeclaredField("failedAttempts");
        f.setAccessible(true);
        f.setInt(v, n);
        return v;
    }

    @Test
    void keepsTheOtpBelowTheLimit() throws Exception {
        when(repo.incrementFailedAttempts(party)).thenReturn(1);
        when(repo.findById(party)).thenReturn(Optional.of(withAttempts(4)));
        tracker.recordFailure(party);
        verify(repo, never()).delete(any());
    }

    @Test
    void destroysTheOtpAtFiveWrongAttempts() throws Exception {
        OtpVerification v = withAttempts(OtpAttemptTracker.MAX_ATTEMPTS);
        when(repo.incrementFailedAttempts(party)).thenReturn(1);
        when(repo.findById(party)).thenReturn(Optional.of(v));
        tracker.recordFailure(party);
        verify(repo).delete(v);
    }

    @Test
    void doesNothingWhenThereIsNoOtp() {
        when(repo.incrementFailedAttempts(party)).thenReturn(0);
        tracker.recordFailure(party);
        verify(repo, never()).findById(any());
        verify(repo, never()).delete(any());
    }
}
