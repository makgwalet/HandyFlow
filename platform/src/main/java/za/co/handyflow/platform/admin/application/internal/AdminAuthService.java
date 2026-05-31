package za.co.handyflow.platform.admin.application.internal;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.admin.domain.model.*;
import za.co.handyflow.platform.admin.domain.repository.*;
import za.co.handyflow.platform.admin.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserRepository              adminUserRepo;
    private final AdminAuditLogRepository          auditRepo;
    private final AdminImpersonationSessionRepository impersonationRepo;
    private final PasswordEncoder                  passwordEncoder;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    // Admin token expiry — 30 minutes as specified
    private static final long ADMIN_TOKEN_EXPIRY_MS = 30 * 60 * 1000L;
    // Impersonation token expiry — 15 minutes, read-only
    private static final long IMPERSONATION_TOKEN_EXPIRY_MS = 15 * 60 * 1000L;

    // ── Step 1: Password login → returns partial session (TOTP required) ───────

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest req, String ipAddress) {
        AdminUser admin = adminUserRepo.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid credentials", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        if (!admin.isActive()) throw new HandyFlowException(
                "Account disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");

        if (admin.isLocked()) throw new HandyFlowException(
                "Account temporarily locked due to too many failed attempts. Try again in 15 minutes.",
                HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED");

        if (!passwordEncoder.matches(req.password(), admin.getPasswordHash())) {
            admin.recordFailedAttempt();
            adminUserRepo.save(admin);
            throw new HandyFlowException(
                    "Invalid credentials", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        // If TOTP not set up yet — return setup required
        if (!admin.isTotpEnabled()) {
            return new AdminLoginResponse(null, admin.getId(), admin.getEmail(),
                    admin.getFullName(), admin.getRole(),
                    "TOTP_SETUP_REQUIRED", null);
        }

        // Password correct, TOTP enabled — return partial token
        // Frontend must call /verify-totp next with this partial token
        String partialToken = generatePartialToken(admin);
        return new AdminLoginResponse(partialToken, admin.getId(), admin.getEmail(),
                admin.getFullName(), admin.getRole(),
                "TOTP_REQUIRED", null);
    }

    // ── Step 2: TOTP verification → returns full access token ─────────────────

    @Transactional
    public AdminLoginResponse verifyTotp(AdminTotpRequest req, String ipAddress) {
        // Validate partial token
        AdminUser admin = validatePartialToken(req.partialToken());

        // Verify TOTP code
        if (!verifyTotpCode(admin.getTotpSecret(), req.code())) {
            admin.recordFailedAttempt();
            adminUserRepo.save(admin);
            throw new HandyFlowException(
                    "Invalid or expired TOTP code", HttpStatus.UNAUTHORIZED, "INVALID_TOTP");
        }

        admin.recordLogin(ipAddress);
        adminUserRepo.save(admin);

        // Audit
        auditRepo.save(AdminAuditLog.create(admin.getId(), admin.getEmail(),
                "ADMIN_LOGIN", "ADMIN_USER", admin.getId().toString(),
                admin.getFullName(), null, ipAddress));

        String token = generateFullToken(admin);
        log.info("Admin login: {} from {}", admin.getEmail(), ipAddress);

        return new AdminLoginResponse(token, admin.getId(), admin.getEmail(),
                admin.getFullName(), admin.getRole(),
                "AUTHENTICATED", Instant.now().plusMillis(ADMIN_TOKEN_EXPIRY_MS));
    }

    // ── TOTP setup ────────────────────────────────────────────────────────────

    @Transactional
    public AdminTotpSetupResponse setupTotp(UUID adminUserId) {
        AdminUser admin = adminUserRepo.findById(adminUserId)
                .orElseThrow(() -> new HandyFlowException(
                        "Admin not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        // Generate a TOTP secret — using Base32 encoded random bytes
        String secret = generateTotpSecret();
        admin.setupTotp(secret);
        adminUserRepo.save(admin);

        // Generate QR code URI for Google Authenticator
        String otpAuthUri = "otpauth://totp/HandyFlow:%s?secret=%s&issuer=HandyFlow%%20Admin&algorithm=SHA1&digits=6&period=30"
                .formatted(admin.getEmail(), secret);

        return new AdminTotpSetupResponse(secret, otpAuthUri);
    }

    @Transactional
    public void confirmTotpSetup(UUID adminUserId, String code) {
        AdminUser admin = adminUserRepo.findById(adminUserId)
                .orElseThrow(() -> new HandyFlowException(
                        "Admin not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!verifyTotpCode(admin.getTotpSecret(), code)) {
            throw new HandyFlowException(
                    "TOTP code incorrect — scan the QR code again and retry",
                    HttpStatus.BAD_REQUEST, "INVALID_TOTP");
        }

        admin.enableTotp();
        adminUserRepo.save(admin);
        log.info("TOTP enabled for admin: {}", admin.getEmail());
    }

    // ── Impersonation ─────────────────────────────────────────────────────────

    @Transactional
    public String impersonateTenant(UUID adminUserId, String adminEmail,
                                     UUID tenantId, String tenantSlug,
                                     String reason, String ipAddress) {
        // Log the impersonation session
        AdminImpersonationSession session = AdminImpersonationSession.create(
                adminUserId, tenantId, adminEmail, tenantSlug, reason, ipAddress);
        impersonationRepo.save(session);

        auditRepo.save(AdminAuditLog.create(adminUserId, adminEmail,
                "IMPERSONATE_TENANT", "TENANT", tenantId.toString(),
                tenantSlug,
                "{\"reason\":\"" + reason + "\",\"sessionId\":\"" + session.getId() + "\"}",
                ipAddress));

        // Generate read-only impersonation JWT — includes tenantId but flagged as IMPERSONATION
        return generateImpersonationToken(tenantId, tenantSlug, adminEmail, session.getId());
    }

    // ── Token generation ──────────────────────────────────────────────────────

    private String generatePartialToken(AdminUser admin) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(admin.getId().toString())
                .claim("email", admin.getEmail())
                .claim("state", "PARTIAL")
                .claim("role", "SUPERADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000L))
                .signWith(key)
                .compact();
    }

    private String generateFullToken(AdminUser admin) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(admin.getId().toString())
                .claim("email", admin.getEmail())
                .claim("role", "SUPERADMIN")
                .claim("adminRole", admin.getRole())
                .claim("fullName", admin.getFullName())
                .claim("state", "AUTHENTICATED")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ADMIN_TOKEN_EXPIRY_MS))
                .signWith(key)
                .compact();
    }

    private String generateImpersonationToken(UUID tenantId, String tenantSlug,
                                               String adminEmail, UUID sessionId) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("IMPERSONATION")
                .claim("tenantId", tenantId.toString())
                .claim("tenantSlug", tenantSlug)
                .claim("adminEmail", adminEmail)
                .claim("sessionId", sessionId.toString())
                .claim("role", "IMPERSONATION")
                .claim("readOnly", true)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + IMPERSONATION_TOKEN_EXPIRY_MS))
                .signWith(key)
                .compact();
    }

    private AdminUser validatePartialToken(String token) {
        try {
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            var claims = Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
                    .parseSignedClaims(token).getPayload();
            String state = (String) claims.get("state");
            if (!"PARTIAL".equals(state)) throw new RuntimeException("Not a partial token");
            UUID adminId = UUID.fromString(claims.getSubject());
            return adminUserRepo.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
        } catch (Exception e) {
            throw new HandyFlowException(
                    "Invalid or expired session. Please log in again.",
                    HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        }
    }

    // ── TOTP helpers ──────────────────────────────────────────────────────────
    // WHY manual implementation? Avoids adding a heavy TOTP library.
    // Standard TOTP (RFC 6238) is just HMAC-SHA1 over time-step + secret.
    // Google Authenticator uses 30-second windows, 6 digits.

    private String generateTotpSecret() {
        // Generate 20 random bytes and base32 encode
        byte[] bytes = new byte[20];
        new java.security.SecureRandom().nextBytes(bytes);
        return base32Encode(bytes);
    }

    private boolean verifyTotpCode(String secret, String code) {
        if (secret == null || code == null) return false;
        long timeStep = System.currentTimeMillis() / 1000 / 30;
        // Check current window and ±1 window for clock drift
        for (int i = -1; i <= 1; i++) {
            String expected = generateTotp(secret, timeStep + i);
            if (expected.equals(code.trim())) return true;
        }
        return false;
    }

    private String generateTotp(String secret, long timeStep) {
        try {
            byte[] key  = base32Decode(secret);
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (timeStep & 0xFF);
                timeStep >>= 8;
            }
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
            byte[] hash   = mac.doFinal(data);
            int    offset = hash[hash.length - 1] & 0xF;
            int    otp    = ((hash[offset] & 0x7F) << 24)
                          | ((hash[offset + 1] & 0xFF) << 16)
                          | ((hash[offset + 2] & 0xFF) << 8)
                          | (hash[offset + 3] & 0xFF);
            otp = otp % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            log.error("TOTP generation failed: {}", e.getMessage());
            return "";
        }
    }

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private String base32Encode(byte[] data) {
        StringBuilder sb  = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        return sb.toString();
    }

    private byte[] base32Decode(String s) {
        s = s.toUpperCase().replaceAll("=", "");
        int outputLength = s.length() * 5 / 8;
        byte[] out = new byte[outputLength];
        int buffer = 0, bitsLeft = 0, index = 0;
        for (char c : s.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[index++] = (byte) (buffer >> bitsLeft);
            }
        }
        return out;
    }
}
