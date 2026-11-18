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
        ReflectionTestUtils.setField(limiter, "negotiateIpPerMinute", 60);
        ReflectionTestUtils.setField(limiter, "negotiateActPerMinute", 20);
        ReflectionTestUtils.setField(limiter, "negotiateOtpRequestPer10Min", 3);
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

    @Test
    void differentIpOrTokenUsesADifferentCounter() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
        limiter.checkSubmit("9.9.9.9", "token-a");
        limiter.checkSubmit("9.9.9.9", "token-b");
        limiter.checkSubmit("8.8.8.8", "token-a");
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(shared, times(3)).tryConsume(keys.capture(), eq(10), eq(60_000L));
        assertEquals(3, keys.getAllValues().stream().distinct().count());
    }

    @Test
    void otpVerifyIsPerSignerNotPerIp() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
        UUID party = UUID.randomUUID();
        limiter.checkOtpVerify(party);
        verify(shared).tryConsume("contracting:otp-verify:" + party, 10, 10 * 60_000L);
    }

    @Test
    void negotiationCodeRequestsAreLimitedPerLinkWhateverTheIp() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
        UUID link = UUID.randomUUID();
        limiter.checkNegotiateOtpRequest(link);
        verify(shared).tryConsume("contracting:negotiate:otp-request:" + link, 3, 10 * 60_000L);
    }

    @Test
    void negotiationIpLimitCapsGuessingAcrossManyTokens() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
        limiter.checkNegotiateIp("9.9.9.9");
        verify(shared).tryConsume("contracting:negotiate:ip:9.9.9.9", 60, 60_000L);
    }

    @Test
    void negotiationActionsUseTheirOwnCounterAndNeverTheRawToken() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
        limiter.checkNegotiateAct("9.9.9.9", "NEGOTIATION-SECRET");
        limiter.checkNegotiateView("9.9.9.9", "NEGOTIATION-SECRET");
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(shared, times(2)).tryConsume(keys.capture(), anyInt(), eq(60_000L));
        assertEquals(2, keys.getAllValues().stream().distinct().count());
        keys.getAllValues().forEach(k -> assertFalse(k.contains("NEGOTIATION-SECRET")));
    }

    @Test
    void negotiationRefusalIsA429() {
        when(shared.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(false);
        assertEquals("RATE_LIMITED", assertThrows(HandyFlowException.class, () -> limiter.checkNegotiateIp("1.1.1.1")).getErrorCode());
    }
}
