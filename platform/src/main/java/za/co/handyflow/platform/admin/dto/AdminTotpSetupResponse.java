package za.co.handyflow.platform.admin.dto;
public record AdminTotpSetupResponse(
        String secret,          // base32 secret to enter manually
        String otpAuthUri       // otpauth:// URI for QR code generation
) {}