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
 * IAM Phase 0.2: it used to switch on whenever sms.enabled=false, which is the default, so a deployment
 * that forgot SMS_ENABLED=true exposed one-time codes to the internet. It now needs its own switch:
 *   handyflow.dev.otp-endpoint.enabled=true   (environment variable DEV_OTP_ENDPOINT=true)
 * and is off everywhere else, whatever sms.enabled is.
 *
 * Usage: GET /api/v1/dev/otp/{partyId}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "handyflow.dev.otp-endpoint.enabled", havingValue = "true", matchIfMissing = false)
public class OtpDevController {

    private final OtpService otpService;

    @jakarta.annotation.PostConstruct
    void warnEnabled() {
        log.warn("DEV OTP endpoint (/api/v1/dev/otp/{partyId}) is ENABLED. It returns signing codes to anyone "
                + "who can reach the API. Never enable handyflow.dev.otp-endpoint.enabled in production.");
    }

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
