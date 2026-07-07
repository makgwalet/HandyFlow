package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.contracting.domain.model.ContractSigningToken;
import za.co.handyflow.platform.contracting.domain.repository.ContractSigningTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Generates and validates signed URL tokens for external party contract signing.
 *
 * Token format: base64url(partyId:contractId:tenantId:expiresEpochSeconds:nonce)
 * signed with HMAC-SHA256 using a server secret.
 *
 * This allows unauthenticated external parties to:
 *   1. View the contract they are being asked to sign
 *   2. Request an OTP to their own phone
 *   3. Submit their OTP to complete signing
 *   4. Post comments / amendment requests
 *   5. Decline to sign with a reason
 *
 * Tokens are single-use: once signing is complete (or declined) the token is
 * marked used. On resend a new token is issued and the old one is revoked.
 *
 * The signing URL is: {baseUrl}/sign/{token}
 * which routes to the public-facing SigningPage on the frontend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SigningTokenService {

    private final ContractSigningTokenRepository signingTokenRepo;

    // FIX: was @Value("${contracting.signing.secret:handyflow-signing-secret-change-in-production}")
    // — a second, dormant copy of the exact same insecure fallback already
    // removed from application.yaml. It's currently unreachable in practice
    // (application.yaml defines contracting.signing.secret via a required
    // env var with no yaml-level default of its own, so this Java-level
    // default only matters if that property were ever removed from
    // application.yaml entirely) — but "currently unreachable" is exactly
    // the kind of latent landmine that resurfaces the next time someone
    // refactors config without knowing this was here. No fallback now.
    @Value("${contracting.signing.secret}")
    private String signingSecret;

    @Value("${contracting.signing.base-url:http://localhost:5173}")
    private String baseUrl;

    @Value("${contracting.signing.token-validity-hours:72}")
    private int tokenValidityHours;

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a signing URL for a specific party on a contract.
     * Format: {baseUrl}/sign/{base64token}
     * The token encodes: partyId, contractId, tenantId, expiry, random nonce
     * and is HMAC-SHA256 signed to prevent tampering.
     */
    public String generateSigningUrl(UUID contractId, UUID partyId, UUID tenantId) {
        String token = generateToken(contractId, partyId, tenantId);
        return baseUrl + "/sign/" + token;
    }

    /**
     * Generates the raw token string (without the base URL prefix).
     * Stored in contract_signing_tokens for validation on incoming requests.
     */
    public String generateToken(UUID contractId, UUID partyId, UUID tenantId) {
        long expiresAt = Instant.now().plus(tokenValidityHours, ChronoUnit.HOURS).getEpochSecond();
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String nonceHex = HexFormat.of().formatHex(nonce);

        String payload = partyId + ":" + contractId + ":" + tenantId + ":" + expiresAt + ":" + nonceHex;
        String signature = hmacSha256(payload, signingSecret);

        String tokenBody = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tokenBody.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates a signing token. Returns the decoded SigningClaims if valid,
     * or throws IllegalArgumentException if expired, tampered, malformed,
     * already used, or revoked.
     * <p>
     * FIX: previously this method ONLY checked the HMAC signature and the
     * expiry embedded in the token's own payload — a purely stateless check
     * against the token string itself, never touching the database. That
     * meant a token could pass this check even after the party had already
     * signed (usedAt set) or after a resend had revoked it (revokedAt set),
     * because those two facts only exist in the database, not in the token.
     * <p>
     * Every one of PublicSigningController's endpoints calls this method
     * first, before doing anything else — {@code submitSign}/{@code decline}
     * happened to get a DB-backed check anyway because
     * {@code ContractingService.signByToken()}/{@code declineByToken()}
     * separately call {@code findValidToken()} — but
     * {@code getContractForSigning}, {@code requestOtp}, {@code addComment},
     * and {@code getComments} had no such second check, so a used or revoked
     * token could still view the contract, request fresh OTPs, and post
     * comments for the rest of its 72-hour validity window. Strengthening
     * this one shared method closes the gap for all of them at once, rather
     * than patching each endpoint (or each ContractingService method)
     * individually.
     */
    public SigningClaims validateToken(String token) {
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 6)
                throw new IllegalArgumentException("Malformed signing token");

            UUID partyId    = UUID.fromString(parts[0]);
            UUID contractId = UUID.fromString(parts[1]);
            UUID tenantId   = UUID.fromString(parts[2]);
            long expiresAt  = Long.parseLong(parts[3]);
            String nonce    = parts[4];
            String signature = parts[5];

            // Verify expiry
            if (Instant.now().getEpochSecond() > expiresAt)
                throw new IllegalArgumentException("Signing link has expired. Request a new one.");

            // Verify signature
            String payload    = partyId + ":" + contractId + ":" + tenantId + ":" + expiresAt + ":" + nonce;
            String expected   = hmacSha256(payload, signingSecret);
            if (!constantTimeEquals(signature, expected))
                throw new IllegalArgumentException("Invalid signing token — possible tampering");

            // FIX: the DB-backed half of validation, previously missing here.
            String tokenHash = sha256(token);
            ContractSigningToken st = signingTokenRepo.findByTokenHash(tokenHash)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Signing link not found — it may have been superseded by a newer one."));
            if (st.getRevokedAt() != null)
                throw new IllegalArgumentException(
                        "This signing link has been revoked. Ask the sender to resend.");
            if (st.getUsedAt() != null)
                throw new IllegalArgumentException(
                        "This signing link has already been used.");

            return new SigningClaims(partyId, contractId, tenantId,
                    Instant.ofEpochSecond(expiresAt));

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid signing token: " + e.getMessage());
        }
    }

    /** Computes SHA-256 of the contract body for tamper detection. */
    public String sha256(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String hmacSha256(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    // Constant-time comparison prevents timing attacks on signature validation
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++)
            result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    public record SigningClaims(UUID partyId, UUID contractId, UUID tenantId, Instant expiresAt) {}
}