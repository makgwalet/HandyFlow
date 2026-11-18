package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Limits for the public signing endpoints and for link reissue. Built on the shared database-backed {@link RateLimiter},
 * so the limits hold across application instances. Starting values, all configurable.
 * <p>
 * Keys use the caller's IP and a short hash of the token, never the token itself, so a token cannot end up in the rate
 * limit table or the logs. The limit is per IP and token together, so people sharing an office network each have their
 * own allowance. A refusal looks the same whether or not the token is valid.
 */
@Component
@RequiredArgsConstructor
public class PublicSigningRateLimiter {

    private static final long MINUTE = 60_000L;

    private final RateLimiter limiter;

    @Value("${contracting.rate-limit.view-per-minute:30}")        private int viewPerMinute;
    @Value("${contracting.rate-limit.comment-per-minute:10}")     private int commentPerMinute;
    @Value("${contracting.rate-limit.decline-per-minute:5}")      private int declinePerMinute;
    @Value("${contracting.rate-limit.submit-per-minute:10}")      private int submitPerMinute;
    @Value("${contracting.rate-limit.otp-verify-per-10-min:10}")  private int otpVerifyPer10Min;
    @Value("${contracting.rate-limit.reissue-per-15-min:3}")      private int reissuePer15Min;

    public void checkView(String ip, String token)    { check("view", ip, token, viewPerMinute, MINUTE); }
    public void checkComment(String ip, String token) { check("comment", ip, token, commentPerMinute, MINUTE); }
    public void checkDecline(String ip, String token) { check("decline", ip, token, declinePerMinute, MINUTE); }
    public void checkSubmit(String ip, String token)  { check("submit", ip, token, submitPerMinute, MINUTE); }

    /** Per signer, whatever the IP, so guesses cannot be spread across addresses. */
    public void checkOtpVerify(UUID partyId) {
        consume("contracting:otp-verify:" + partyId, otpVerifyPer10Min, 10 * MINUTE);
    }

    /** Per staff user and contract. */
    public void checkReissue(UUID userId, UUID contractId) {
        consume("contracting:reissue:" + userId + ":" + contractId, reissuePer15Min, 15 * MINUTE);
    }

    private void check(String what, String ip, String token, int max, long windowMs) {
        consume("contracting:sign:" + what + ":" + ip + ":" + shortHash(token), max, windowMs);
    }

    private void consume(String key, int max, long windowMs) {
        if (!limiter.tryConsume(key, max, windowMs)) {
            throw new HandyFlowException("Too many requests. Please wait a moment and try again.",
                    HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        }
    }

    private static String shortHash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(String.valueOf(token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
