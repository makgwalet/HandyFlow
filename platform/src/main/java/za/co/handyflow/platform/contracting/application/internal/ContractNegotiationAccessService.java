package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.SmsService;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * How a counterparty reaches a negotiation. A negotiation link is its own credential, separate from any signing link: a random token,
 * stored only as its SHA-256, that expires and can be revoked. Holding the link is not enough; the counterparty confirms a one-time code sent to
 * the contact details staff recorded for them and receives a short-lived session. Every later request needs both. Everything a counterparty
 * does is delegated to {@link ContractNegotiationService}, which can only record proposals and comments, never change the contract.
 * <p>
 * A bad, expired, revoked or superseded link all look the same to the caller, so nothing can be learned by probing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractNegotiationAccessService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final ContractRepository contractRepo;
    private final ContractPartyRepository partyRepo;
    private final ContractNegotiationRepository negotiationRepo;
    private final ContractNegotiationLinkRepository linkRepo;
    private final ContractNegotiationSessionRepository sessionRepo;
    private final ContractNegotiationEventRepository eventRepo;
    private final ContractNegotiationService negotiation;
    private final OtpService otpService;
    private final PublicSigningRateLimiter rateLimiter;
    private final EmailService emailService;
    private final SmsService smsService;

    @Value("${contracting.signing.base-url:http://localhost:5173}") private String baseUrl;
    @Value("${contracting.negotiation.link-validity-days:14}") private int linkValidityDays;
    @Value("${contracting.negotiation.session-minutes:60}") private int sessionMinutes;

    private final SecureRandom random = new SecureRandom();

    /** Who is calling, once the link and session have both been checked. */
    public record Access(TenantId tenantId, UUID contractId, UUID partyId, UUID linkId) {}

    // ── Staff ────────────────────────────────────────────────────────────────

    /** Issues a fresh link for one party and revokes that party's earlier links. The raw link is in the result only. */
    @Transactional
    public IssuedLinkView issueLink(TenantId tenantId, UUID contractId, UUID partyId, UUID by) {
        Contract c = contractRepo.findActiveById(tenantId, contractId).orElseThrow(() -> new ResourceNotFoundException("Contract", contractId.toString()));
        if (!"DRAFT".equals(c.getStatus()) && !"UNDER_REVIEW".equals(c.getStatus()))
            throw new IllegalStateException("A negotiation link can only be issued while the contract is a draft or under review");
        ContractNegotiation n = negotiationRepo.findByTenantIdAndContractId(tenantId.getValue(), contractId)
                .orElseThrow(() -> new IllegalStateException("Open the negotiation before inviting a counterparty"));
        if (!n.isActive()) throw new IllegalStateException("This negotiation is " + n.getStatus() + ". Reopen it before inviting a counterparty.");
        ContractParty party = partyRepo.findByContract(contractId).stream().filter(p -> p.getId().equals(partyId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));
        boolean hasEmail = party.getEmail() != null && !party.getEmail().isBlank();
        boolean hasPhone = party.getPhone() != null && !party.getPhone().isBlank();
        if (!hasEmail && !hasPhone) throw new IllegalStateException("This party has no email address or phone number to send the link and codes to");

        for (ContractNegotiationLink old : linkRepo.findByTenantIdAndContractIdAndPartyIdAndRevokedAtIsNull(tenantId.getValue(), contractId, partyId)) {
            old.revoke("Replaced by a newer link");
            linkRepo.save(old);
        }
        String token = newToken();
        Instant expires = Instant.now().plus(linkValidityDays, ChronoUnit.DAYS);
        if (n.getResponseDeadline() != null) {
            Instant endOfDeadline = n.getResponseDeadline().plusDays(1).atStartOfDay(ZONE).toInstant();
            if (endOfDeadline.isBefore(expires)) expires = endOfDeadline;
        }
        ContractNegotiationLink link = ContractNegotiationLink.issue(tenantId.getValue(), contractId, n.getId(), partyId, sha256(token), expires, by);
        link.recordDelivery(hasEmail ? "QUEUED" : "NO_CHANNEL");
        linkRepo.save(link);
        String url = baseUrl + "/negotiate/" + token;
        if (hasEmail) emailService.send(party.getEmail(), "Review and propose changes: " + c.getTitle(), invitation(party.getFullName(), c.getTitle(), url, expires));
        audit(link, "STAFF", by, partyId, "LINK_ISSUED", "Link valid until " + expires, null);
        return new IssuedLinkView(link.getId(), partyId, url, expires, link.getDeliveryStatus());
    }

    @Transactional
    public void revokeLink(TenantId tenantId, UUID contractId, UUID linkId, UUID by) {
        ContractNegotiationLink l = linkRepo.findByTenantIdAndId(tenantId.getValue(), linkId).filter(x -> x.getContractId().equals(contractId))
                .orElseThrow(() -> new ResourceNotFoundException("Link", linkId.toString()));
        l.revoke("Revoked by staff");
        linkRepo.save(l);
        audit(l, "STAFF", by, l.getPartyId(), "LINK_REVOKED", null, null);
    }

    @Transactional(readOnly = true)
    public List<LinkView> links(TenantId tenantId, UUID contractId) {
        Map<UUID, String> names = new HashMap<>();
        partyRepo.findByContract(contractId).forEach(p -> names.put(p.getId(), p.getFullName()));
        Instant now = Instant.now();
        return linkRepo.findByTenantIdAndContractIdOrderByCreatedAtDesc(tenantId.getValue(), contractId).stream()
                .map(l -> new LinkView(l.getId(), l.getPartyId(), names.get(l.getPartyId()), l.getExpiresAt(), l.getRevokedAt(), l.getRevokedReason(), l.getDeliveryStatus(),
                        l.getLastAccessedAt(), l.getCreatedAt(), l.isActive(now))).toList();
    }

    // ── Counterparty: before and during identity check ───────────────────────

    /** What the link page may show before the code is confirmed. */
    @Transactional(readOnly = true)
    public LinkInfo info(String token) {
        ContractNegotiationLink l = resolve(token);
        Contract c = contractOf(l);
        ContractParty p = partyOf(l);
        return new LinkInfo(c.getTitle(), channelOf(p), maskedDestination(p), l.getExpiresAt());
    }

    // Not @Transactional: the rate limiter must commit its count even when the request is then refused.
    public OtpSentView requestCode(String token, String ip) {
        ContractNegotiationLink l = resolve(token);
        Contract c = contractOf(l);
        ContractParty p = partyOf(l);
        rateLimiter.checkNegotiateOtpRequest(l.getId());
        String channel = channelOf(p);
        String code = otpService.generateAndStore(l.getId().toString());
        if ("SMS".equals(channel)) {
            boolean sent = false;
            try { sent = smsService.send(p.getPhone(), "HandyFlow contract review code: " + code + " for \"" + shorten(c.getTitle()) + "\". Valid 10 min. Do not share."); }
            catch (Exception e) { log.error("Negotiation code SMS failed for link={}: {}", l.getId(), e.getMessage()); }
            if (!sent) log.warn("Negotiation code SMS not delivered for link={}", l.getId());
        } else {
            emailService.send(p.getEmail(), "Your HandyFlow contract review code",
                    "<p>Your code is <strong>" + HtmlUtils.htmlEscape(code) + "</strong>.</p><p>It is valid for 10 minutes. Do not share it.</p>");
        }
        audit(l, "COUNTERPARTY", null, p.getId(), "CODE_REQUESTED", channel, ip);
        return new OtpSentView(channel, maskedDestination(p));
    }

    /** Confirms the code. Returns null on a wrong, expired or used-up code, after recording it, so the attempt is audited. */
    // Not @Transactional, for the same reason: the attempt count and the audit of a wrong code must survive.
    public SessionView verifyCode(String token, String code, String ip) {
        ContractNegotiationLink l = resolve(token);
        rateLimiter.checkOtpVerify(l.getId());
        if (!otpService.verify(l.getId().toString(), code)) {
            audit(l, "COUNTERPARTY", null, l.getPartyId(), "CODE_FAILED", null, ip);
            return null;
        }
        sessionRepo.deleteExpired(Instant.now().minus(1, ChronoUnit.DAYS));
        String sessionToken = newToken();
        Instant expires = Instant.now().plus(sessionMinutes, ChronoUnit.MINUTES);
        sessionRepo.save(ContractNegotiationSession.start(l.getTenantId(), l.getId(), sha256(sessionToken), expires, ip));
        l.touch();
        linkRepo.save(l);
        audit(l, "COUNTERPARTY", null, l.getPartyId(), "ACCESS_VERIFIED", null, ip);
        return new SessionView(sessionToken, expires);
    }

    // ── Counterparty: once verified ──────────────────────────────────────────

    /** Checks link and session together. Every data request goes through here. */
    @Transactional
    public Access authenticate(String token, String sessionToken) {
        ContractNegotiationLink l = resolve(token);
        if (sessionToken == null || sessionToken.isBlank()) throw sessionRequired();
        ContractNegotiationSession s = sessionRepo.findBySessionHash(sha256(sessionToken)).orElseThrow(ContractNegotiationAccessService::sessionRequired);
        if (!s.getLinkId().equals(l.getId()) || !s.isActive(Instant.now())) throw sessionRequired();
        l.touch();
        linkRepo.save(l);
        return new Access(TenantId.of(l.getTenantId()), l.getContractId(), l.getPartyId(), l.getId());
    }

    public CounterpartyView view(Access a) { return negotiation.counterpartyView(a.tenantId(), a.contractId(), a.partyId()); }

    public ProposalView propose(Access a, ProposalInput in, String ip) { return negotiation.submitProposal(a.tenantId(), a.contractId(), a.partyId(), in, ip); }

    public ProposalView accept(Access a, UUID proposalId, String note, String ip) { return negotiation.counterpartyAccept(a.tenantId(), a.contractId(), a.partyId(), proposalId, note, ip); }

    public ProposalView reject(Access a, UUID proposalId, String reason, String ip) { return negotiation.counterpartyReject(a.tenantId(), a.contractId(), a.partyId(), proposalId, reason, ip); }

    public ProposalView counter(Access a, UUID proposalId, CounterInput in, String ip) { return negotiation.counterpartyCounter(a.tenantId(), a.contractId(), a.partyId(), proposalId, in, ip); }

    public ProposalView withdraw(Access a, UUID proposalId, String ip) { return negotiation.withdraw(a.tenantId(), a.contractId(), a.partyId(), proposalId, ip); }

    public CommentView comment(Access a, CounterpartyCommentInput in, String ip) { return negotiation.counterpartyComment(a.tenantId(), a.contractId(), a.partyId(), in, ip); }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** The one place a link token is turned into a link. Same refusal for unknown, expired, revoked and for a negotiation that has ended. */
    ContractNegotiationLink resolve(String token) {
        if (token == null || token.length() < 20 || token.length() > 200) throw invalid();
        ContractNegotiationLink l = linkRepo.findByTokenHash(sha256(token)).orElseThrow(ContractNegotiationAccessService::invalid);
        if (!l.isActive(Instant.now())) throw invalid();
        ContractNegotiation n = negotiationRepo.findById(l.getNegotiationId()).orElseThrow(ContractNegotiationAccessService::invalid);
        if (!n.isActive()) throw invalid();
        Contract c = contractRepo.findActiveById(TenantId.of(l.getTenantId()), l.getContractId()).orElseThrow(ContractNegotiationAccessService::invalid);
        if (!"DRAFT".equals(c.getStatus()) && !"UNDER_REVIEW".equals(c.getStatus())) throw invalid();
        return l;
    }

    private Contract contractOf(ContractNegotiationLink l) {
        return contractRepo.findActiveById(TenantId.of(l.getTenantId()), l.getContractId()).orElseThrow(ContractNegotiationAccessService::invalid);
    }

    private ContractParty partyOf(ContractNegotiationLink l) {
        return partyRepo.findByContract(l.getContractId()).stream().filter(p -> p.getId().equals(l.getPartyId())).findFirst().orElseThrow(ContractNegotiationAccessService::invalid);
    }

    static HandyFlowException invalid() {
        return new HandyFlowException("This link is not valid or has expired. Ask the sender for a new one.", HttpStatus.NOT_FOUND, "NEGOTIATION_LINK_INVALID");
    }

    static HandyFlowException sessionRequired() {
        return new HandyFlowException("Please confirm your code to continue.", HttpStatus.UNAUTHORIZED, "NEGOTIATION_SESSION_REQUIRED");
    }

    private void audit(ContractNegotiationLink l, String actor, UUID userId, UUID partyId, String type, String detail, String ip) {
        eventRepo.save(ContractNegotiationEvent.of(l.getTenantId(), l.getContractId(), l.getNegotiationId(), actor, userId, partyId, type, detail, ip));
    }

    static String channelOf(ContractParty p) { return p.getPhone() != null && !p.getPhone().isBlank() ? "SMS" : "EMAIL"; }

    static String maskedDestination(ContractParty p) {
        if ("SMS".equals(channelOf(p))) {
            String d = p.getPhone().replaceAll("\\s+", "");
            return d.length() <= 4 ? "****" : "*".repeat(d.length() - 4) + d.substring(d.length() - 4);
        }
        String e = p.getEmail();
        int at = e.indexOf('@');
        return at <= 1 ? "****" + e.substring(Math.max(at, 0)) : e.charAt(0) + "****" + e.substring(at);
    }

    private static String shorten(String s) { return s.length() > 35 ? s.substring(0, 32) + "..." : s; }

    private String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String invitation(String name, String title, String url, Instant expires) {
        return "<p>Hello " + HtmlUtils.htmlEscape(name) + ",</p>"
                + "<p>You have been invited to review <strong>" + HtmlUtils.htmlEscape(title) + "</strong> and propose changes to its wording.</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(url) + "\">Open the contract</a></p>"
                + "<p>You will be asked to confirm a one-time code. Your proposals are requests only; the sender decides what changes. "
                + "This link expires on " + LocalDate.ofInstant(expires, ZONE) + ".</p>";
    }
}
