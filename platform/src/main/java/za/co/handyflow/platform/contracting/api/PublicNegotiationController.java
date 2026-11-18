package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractNegotiationAccessService;
import za.co.handyflow.platform.contracting.application.internal.ContractNegotiationAccessService.Access;
import za.co.handyflow.platform.contracting.application.internal.PublicSigningRateLimiter;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.UUID;

/**
 * Counterparty side of negotiation. No login: the link token is one credential and the session from confirming a one-time code is the other,
 * and every request after the code needs both. Nothing here can change the contract: a counterparty can only record proposals and comments.
 * Every request is rate limited per IP, and per IP and link, before anything is looked up. Tenant, contract and party always come from the link,
 * never from the request.
 */
@RestController
@RequestMapping("/api/v1/negotiate/{token}")
@RequiredArgsConstructor
@Tag(name = "Public negotiation", description = "Link and one-time-code endpoints for counterparties")
public class PublicNegotiationController {

    static final String SESSION_HEADER = "X-Negotiation-Session";

    private final ContractNegotiationAccessService access;
    private final PublicSigningRateLimiter rateLimiter;

    @GetMapping
    @Operation(summary = "What the link is for, before the code is confirmed")
    public ResponseEntity<ApiResponse<LinkInfo>> info(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.checkNegotiateIp(http.getRemoteAddr());
        rateLimiter.checkNegotiateView(http.getRemoteAddr(), token);
        return ResponseEntity.ok(ApiResponse.success("Link", access.info(token)));
    }

    @PostMapping("/code")
    @Operation(summary = "Send a one-time code to the contact details held for this party")
    public ResponseEntity<ApiResponse<OtpSentView>> code(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.checkNegotiateIp(http.getRemoteAddr());
        rateLimiter.checkNegotiateView(http.getRemoteAddr(), token);
        return ResponseEntity.ok(ApiResponse.success("Code sent", access.requestCode(token, http.getRemoteAddr())));
    }

    @PostMapping("/verify")
    @Operation(summary = "Confirm the code and receive a short-lived session")
    public ResponseEntity<ApiResponse<SessionView>> verify(@PathVariable String token, @Valid @RequestBody VerifyRequest q, HttpServletRequest http) {
        rateLimiter.checkNegotiateIp(http.getRemoteAddr());
        rateLimiter.checkNegotiateView(http.getRemoteAddr(), token);
        SessionView s = access.verifyCode(token, q.code(), http.getRemoteAddr());
        if (s == null) throw new HandyFlowException("That code is not correct or has expired.", HttpStatus.UNAUTHORIZED, "NEGOTIATION_CODE_INVALID");
        return ResponseEntity.ok(ApiResponse.success("Verified", s));
    }

    @GetMapping("/negotiation")
    @Operation(summary = "The current wording, this party's proposals and the comments addressed to them")
    public ResponseEntity<ApiResponse<CounterpartyView>> view(@PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
                                                              HttpServletRequest http) {
        Access a = authenticate(token, session, http, false);
        return ResponseEntity.ok(ApiResponse.success("Negotiation", access.view(a)));
    }

    @PostMapping("/proposals")
    @Operation(summary = "Propose an addition, deletion or replacement. It is a request: the contract does not change")
    public ResponseEntity<ApiResponse<ProposalView>> propose(@PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
                                                             @Valid @RequestBody ProposalInput q, HttpServletRequest http) {
        Access a = authenticate(token, session, http, true);
        return ResponseEntity.ok(ApiResponse.success("Proposal submitted", access.propose(a, q, http.getRemoteAddr())));
    }

    @PostMapping("/proposals/{proposalId}/withdraw")
    @Operation(summary = "Withdraw your own proposal while it has not been applied")
    public ResponseEntity<ApiResponse<ProposalView>> withdraw(@PathVariable String token, @PathVariable UUID proposalId,
                                                              @RequestHeader(value = SESSION_HEADER, required = false) String session, HttpServletRequest http) {
        Access a = authenticate(token, session, http, true);
        return ResponseEntity.ok(ApiResponse.success("Proposal withdrawn", access.withdraw(a, proposalId, http.getRemoteAddr())));
    }

    @PostMapping("/comments")
    @Operation(summary = "Comment on a clause or proposal, or reply to staff")
    public ResponseEntity<ApiResponse<CommentView>> comment(@PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
                                                            @Valid @RequestBody CounterpartyCommentInput q, HttpServletRequest http) {
        Access a = authenticate(token, session, http, true);
        return ResponseEntity.ok(ApiResponse.success("Comment posted", access.comment(a, q, http.getRemoteAddr())));
    }

    private Access authenticate(String token, String session, HttpServletRequest http, boolean acting) {
        rateLimiter.checkNegotiateIp(http.getRemoteAddr());
        if (acting) rateLimiter.checkNegotiateAct(http.getRemoteAddr(), token);
        else rateLimiter.checkNegotiateView(http.getRemoteAddr(), token);
        return access.authenticate(token, session);
    }
}
