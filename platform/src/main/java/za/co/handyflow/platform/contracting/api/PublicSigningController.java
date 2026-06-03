package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractingService;
import za.co.handyflow.platform.contracting.application.internal.SigningTokenService;
import za.co.handyflow.platform.contracting.application.internal.SigningTokenService.SigningClaims;
import za.co.handyflow.platform.contracting.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/**
 * Public-facing signing endpoints — no JWT required.
 * All endpoints validate the HMAC-SHA256 signing token instead.
 *
 * FIX §9: The original ContractingController had a ?tenantId query param as a
 * workaround because TenantContext is null for unauthenticated requests.
 * The signing token ALREADY contains tenantId in its payload (SigningClaims.tenantId()).
 * We now always extract tenantId from the validated token — never from a query param,
 * never from TenantContext. This eliminates both the NPE risk and the security concern
 * of a caller supplying an arbitrary tenantId.
 *
 * Signing URL format sent to parties:  {baseUrl}/sign/{token}
 * The frontend SigningPage calls these endpoints using the token from the URL.
 *
 * Endpoints:
 *   GET  /api/v1/sign/{token}/contract   — fetch contract for display
 *   POST /api/v1/sign/{token}/otp        — request OTP (rate-limited: 3/10min)
 *   POST /api/v1/sign/{token}/submit     — submit OTP to complete signing
 *   POST /api/v1/sign/{token}/decline    — formally decline with reason
 *   POST /api/v1/sign/{token}/comment    — post a comment or amendment request
 *   GET  /api/v1/sign/{token}/comments   — list all comments on this contract
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sign")
@RequiredArgsConstructor
@Tag(name = "Public Signing", description = "Token-authenticated endpoints for external party signing")
public class PublicSigningController {

    private final ContractingService  contractingService;
    private final SigningTokenService tokenService;

    @GetMapping("/{token}/contract")
    @Operation(summary = "Fetch contract content for the signing party — marks first view")
    public ResponseEntity<ApiResponse<PublicContractView>> getContractForSigning(
            @PathVariable String token) {
        SigningClaims claims = tokenService.validateToken(token);
        // FIX: tenantId always comes from the validated token, never from TenantContext
        TenantId tenantId = TenantId.of(claims.tenantId());
        PublicContractView view = contractingService.getPublicContractView(
                tenantId, claims.contractId(), claims.partyId(), token);
        return ResponseEntity.ok(ApiResponse.success("Contract loaded", view));
    }

    @PostMapping("/{token}/otp")
    @Operation(summary = "Request OTP — sent to party's registered phone. Rate-limited: 3/10 min.")
    public ResponseEntity<ApiResponse<Void>> requestOtp(@PathVariable String token) {
        SigningClaims claims = tokenService.validateToken(token);
        TenantId tenantId = TenantId.of(claims.tenantId());
        contractingService.requestOtpByToken(tenantId, claims.contractId(), claims.partyId());
        return ResponseEntity.ok(ApiResponse.success("OTP sent to your registered phone number", null));
    }

    @PostMapping("/{token}/submit")
    @Operation(summary = "Submit OTP to complete signing — marks token used on success")
    public ResponseEntity<ApiResponse<SigningResultView>> submitSign(
            @PathVariable String token,
            @Valid @RequestBody SignContractRequest req,
            HttpServletRequest http) {
        SigningClaims claims = tokenService.validateToken(token);
        TenantId tenantId = TenantId.of(claims.tenantId());
        String ip = http.getRemoteAddr();
        String ua = http.getHeader("User-Agent");
        SigningResultView result = contractingService.signByToken(
                tenantId, claims.contractId(), claims.partyId(), req, ip, ua, token);
        return ResponseEntity.ok(ApiResponse.success("Contract signed", result));
    }

    @PostMapping("/{token}/decline")
    @Operation(summary = "Formally decline to sign — notifies contract owner, revokes token")
    public ResponseEntity<ApiResponse<Void>> decline(
            @PathVariable String token,
            @RequestBody DeclineSignRequest req) {
        SigningClaims claims = tokenService.validateToken(token);
        TenantId tenantId = TenantId.of(claims.tenantId());
        contractingService.declineByToken(tenantId, claims.contractId(), claims.partyId(),
                req.reason());
        return ResponseEntity.ok(ApiResponse.success("Your decision has been recorded", null));
    }

    @PostMapping("/{token}/comment")
    @Operation(summary = "Post a comment or amendment request — visible to all parties and owner")
    public ResponseEntity<ApiResponse<CommentView>> addComment(
            @PathVariable String token,
            @Valid @RequestBody AddCommentRequest req) {
        SigningClaims claims = tokenService.validateToken(token);
        TenantId tenantId = TenantId.of(claims.tenantId());
        CommentView comment = contractingService.addCommentByToken(
                tenantId, claims.contractId(), claims.partyId(), req);
        return ResponseEntity.ok(ApiResponse.success("Comment posted", comment));
    }

    @GetMapping("/{token}/comments")
    @Operation(summary = "List all comments on this contract")
    public ResponseEntity<ApiResponse<List<CommentView>>> getComments(
            @PathVariable String token) {
        SigningClaims claims = tokenService.validateToken(token);
        TenantId tenantId = TenantId.of(claims.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Comments",
                contractingService.getCommentsByToken(
                        tenantId, claims.contractId(), claims.partyId())));
    }
}
