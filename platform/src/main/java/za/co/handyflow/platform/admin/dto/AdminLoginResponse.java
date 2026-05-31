package za.co.handyflow.platform.admin.dto;
import java.time.Instant;
import java.util.UUID;
public record AdminLoginResponse(
        String  token,
        UUID    adminId,
        String  email,
        String  fullName,
        String  role,
        String  state,          // TOTP_SETUP_REQUIRED | TOTP_REQUIRED | AUTHENTICATED
        Instant expiresAt
) {}