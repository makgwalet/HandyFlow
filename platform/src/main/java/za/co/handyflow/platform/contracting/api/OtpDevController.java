package za.co.handyflow.platform.contracting.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.OtpService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

/**
 * DEV-ONLY endpoint for retrieving OTP codes during development and testing.
 *
 * FIX §14: Original had matchIfMissing = true, meaning the endpoint was ACTIVE
 * by default on any deployment that didn't explicitly set sms.enabled.
 * Changed to matchIfMissing = false — the endpoint is now DISABLED unless you
 * explicitly set sms.enabled=false in application.yaml or an env var.
 *
 * Production config must have: sms.enabled=true
 * Dev/test config must have:   sms.enabled=false   (to enable this endpoint)
 *
 * Usage: GET /api/v1/dev/otp/{partyId}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.enabled", havingValue = "false", matchIfMissing = false)
public class OtpDevController {

    private final OtpService otpService;

    @GetMapping("/otp/{partyId}")
    public ResponseEntity<ApiResponse<String>> getOtp(@PathVariable UUID partyId) {
        String otp = otpService.getStoredOtp(partyId.toString());
        if (otp == null) {
            return ResponseEntity.ok(ApiResponse.success(
                    "No active OTP for this party — request one first via POST /sign/{token}/otp",
                    null));
        }
        log.warn("DEV: OTP retrieved via dev endpoint for partyId={} — NEVER deploy this in production",
                partyId);
        return ResponseEntity.ok(ApiResponse.success("OTP (dev mode)", otp));
    }
}
