package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicSigningRateLimiterTest {

    private final RateLimiter shared = mock(RateLimiter.class);
    private final PublicSigningRateLimiter limiter = new PublicSigningRateLimiter(shared);

    PublicSigningRateLimiterTest() {
        ReflectionTestUtils.setField(limiter, "viewPerMinute", 30);
        ReflectionTestUtils.setField(limiter, "commentPerMinute", 10);
        ReflectionTestUtils.setField(limiter, "declinePerMinute", 5);
        ReflectionTestUtils.setField(limiter, "submitPerMinute", 10);
        ReflectionTestUtils.setField(limiter, "otpVerifyPer10Min", 10);
        ReflectionTestUtils.setField(limiter, "reissuePer15Min", 3);
    }

    @Test
    void allowsRequestsUnderTheLimit() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
        assertDoesNotThrow(() -> limiter.checkView("203.0.113.5", "tok"));
    }

    @Test
    void refusesWith429AndAnIdenticalErrorWhateverTheToken() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(false);
        HandyFlowException a = assertThrows(HandyFlowException.class, () -> limiter.checkDecline("203.0.113.5", "valid-token"));
        HandyFlowException b = assertThrows(HandyFlowException.class, () -> limiter.checkDecline("203.0.113.5", "garbage"));
        assertEquals("RATE_LIMITED", a.getErrorCode());
        assertEquals(a.getMessage(), b.getMessage());
    }

    @Test
    void neverPutsTheRawTokenInTheKey() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
        limiter.checkComment("203.0.113.5", "SECRET-TOKEN-VALUE");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(shared).tryConsume(key.capture(), eq(10), eq(60_000L));
        assertFalse(key.getValue().contains("SECRET-TOKEN-VALUE"));
        assertTrue(key.getValue().contains("203.0.113.5"));
    }

    @Test
    void reissueIsThreePerFifteenMinutesPerUserAndContract() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
        UUID user = UUID.randomUUID();
        UUID contract = UUID.randomUUID();
        limiter.checkReissue(user, contract);
        verify(shared).tryConsume("contracting:reissue:" + user + ":" + contract, 3, 15 * 60_000L);
    }
}
