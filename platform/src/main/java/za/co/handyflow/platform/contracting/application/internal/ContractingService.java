package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.SmsService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractingService {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final ContractRepository             contractRepo;
    private final ContractTemplateRepository     templateRepo;
    private final ContractPartyRepository        partyRepo;
    private final ContractSignatureRepository    signatureRepo;
    private final ContractCommentRepository      commentRepo;
    private final ContractSigningTokenRepository signingTokenRepo;
    private final ContractNumberGenerator        numberGen;
    private final ContractTemplateSeeder         templateSeeder;
    private final OtpService                     otpService;
    private final ContractPdfGenerator           pdfGenerator;
    private final ContractVariableResolver       variableResolver;
    private final SigningTokenService            signingTokenService;
    private final EmailService                   emailService;
    private final SmsService                     smsService;    // FIX: SMS now wired
    private final OtpRateLimitRepository          otpRateLimitRepo;

    @Value("${contracting.signing.base-url:http://localhost:5173}")
    private String baseUrl;

    @Value("${contracting.owner.name:HandyFlow Admin}")
    private String ownerDisplayName;

    @Value("${contracting.owner.email:makgwale10111@gmail.com}")
    private String ownerEmail;

    // FIX: every smsService.send(...) call in this class used to run
    // synchronously, directly inside a @Transactional business method. That's
    // the same "notification failure poisons the business transaction"
    // problem already found and fixed once for TenantAdminRecipientsImpl —
    // if the SMS provider throws (timeout, invalid number, API error), that
    // exception propagates straight up and rolls back whatever real business
    // state was just about to commit (a party's decline, a signature, etc.).
    // This wrapper is the fix: it catches anything the provider throws and
    // returns false, so a flaky SMS gateway can never take down a legally
    // significant contract action with it. Every call site below now goes
    // through this instead of calling smsService.send(...) directly.
    private boolean safeSms(String phone, String message) {
        if (phone == null || phone.isBlank()) {
            log.warn("Skipping SMS — no phone number available. message={}", truncate(message, 60));
            return false;
        }
        try {
            return smsService.send(phone, message);
        } catch (Exception e) {
            log.error("SMS send failed to={} (business action proceeds regardless): {}",
                    phone, e.getMessage(), e);
            return false;
        }
    }

    private static final DateTimeFormatter SAST_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm")
                    .withZone(ZoneId.of("Africa/Johannesburg"));

    // ── OTP Rate limiting ─────────────────────────────────────────────────────
    // FIX: rate limiting is now database-backed via OtpRateLimit/
    // OtpRateLimitRepository instead of an in-memory ConcurrentHashMap — see
    // OtpRateLimit's Javadoc for why (same multi-instance problem as
    // OtpService.hashStore, fixed the same way: a table instead of Redis,
    // since this traffic volume doesn't justify standing up new
    // infrastructure). Semantics are UNCHANGED from the original
    // ConcurrentHashMap-based version — same thresholds, same window,
    // same lockout behavior on repeated failures — only the storage
    // mechanism changed.

    private static final int  MAX_OTP_REQUESTS = 3;
    private static final int  MAX_OTP_FAILURES = 5;
    private static final long RATE_WINDOW_MS   = 10 * 60 * 1_000L;

    private void checkOtpRateLimit(String partyId) {
        UUID partyUuid = UUID.fromString(partyId);
        // Pessimistic lock: makes this read-modify-write atomic across
        // instances, replacing what ConcurrentHashMap.compute(...) gave us
        // atomically within one JVM — see OtpRateLimitRepository's Javadoc.
        OtpRateLimit rate = otpRateLimitRepo.findByPartyIdForUpdate(partyUuid).orElse(null);

        if (rate == null) {
            otpRateLimitRepo.save(OtpRateLimit.startNewWindow(partyUuid, 1, 0));
            return;
        }
        if (rate.isWindowExpired(RATE_WINDOW_MS)) {
            rate.resetWindow(1, 0);
            otpRateLimitRepo.save(rate);
            return;
        }
        if (rate.getRequestCount() >= MAX_OTP_REQUESTS) {
            throw new IllegalStateException(
                    "Too many OTP requests. Please wait 10 minutes before trying again.");
        }
        rate.incrementRequestCount();
        otpRateLimitRepo.save(rate);
    }

    private void recordOtpFailure(String partyId) {
        UUID partyUuid = UUID.fromString(partyId);
        OtpRateLimit rate = otpRateLimitRepo.findByPartyIdForUpdate(partyUuid).orElse(null);

        if (rate == null) {
            otpRateLimitRepo.save(OtpRateLimit.startNewWindow(partyUuid, 0, 1));
            return;
        }
        rate.incrementFailCount();
        if (rate.getFailCount() >= MAX_OTP_FAILURES) {
            rate.lockOutRequests(MAX_OTP_REQUESTS);
        }
        otpRateLimitRepo.save(rate);
    }

    private void clearOtpFailures(String partyId) {
        UUID partyUuid = UUID.fromString(partyId);
        otpRateLimitRepo.findByPartyIdForUpdate(partyUuid).ifPresent(rate -> {
            rate.clearFailures();
            otpRateLimitRepo.save(rate);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Templates
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public List<TemplateResponse> getTemplates(TenantId tenantId) {
        templateSeeder.seedForTenant(tenantId);
        return templateRepo.findAllActive(tenantId)
                .stream().map(this::toTemplateResponse).toList();
    }

    @Transactional
    public TemplateResponse createTemplate(TenantId tenantId, CreateTemplateRequest req) {
        ContractTemplate t = ContractTemplate.create(tenantId, req.name(),
                req.contractType(), req.description(), req.bodyTemplate(),
                req.variables(), false);
        templateRepo.save(t);
        return toTemplateResponse(t);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Contracts
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<ContractSummaryResponse> getContracts(TenantId tenantId, String status,
                                                      String type, Pageable pageable) {
        return contractRepo.findAllActive(tenantId, status, type, pageable)
                .map(c -> {
                    List<ContractParty> parties = partyRepo.findByContract(c.getId());
                    long signed = parties.stream()
                            .filter(p -> "SIGNED".equals(p.getSigningStatus())).count();
                    // NEW: was previously computed on the frontend from a
                    // `comments` field this response never actually had —
                    // see ContractSummaryResponse.unresolvedAmendmentCount's
                    // comment. Matches this method's existing per-row query
                    // style (parties are already fetched the same way, one
                    // query per row) rather than introducing a differently-
                    // shaped batch query for just this one field.
                    int unresolved = commentRepo.findUnresolvedAmendments(c.getId()).size();
                    return toContractSummary(c, parties.size(), (int) signed, unresolved);
                });
    }

    @Transactional(readOnly = true)
    public ContractResponse getContract(TenantId tenantId, UUID id) {
        Contract c = findActive(tenantId, id);
        List<ContractParty> parties = partyRepo.findByContract(id);
        c.setParties(parties);
        return toContractResponse(c);
    }

    @Transactional
    public ContractResponse createContract(TenantId tenantId, CreateContractRequest req,
                                           UUID createdByUserId) {
        templateSeeder.seedForTenant(tenantId);

        String body = req.body();
        if (body == null && req.templateId() != null) {
            ContractTemplate tmpl = templateRepo.findActiveById(tenantId, req.templateId())
                    .orElseThrow(() -> new ResourceNotFoundException("Template",
                            req.templateId().toString()));
            body = tmpl.getBodyTemplate();
        }
        if (body == null) body = "<p>Contract body — edit to add content.</p>";

        // Sanitise variable values before substitution to prevent HTML injection
        if (req.variables() != null && !req.variables().isEmpty()) {
            Map<String, String> safeVars = new HashMap<>();
            for (var entry : req.variables().entrySet())
                safeVars.put(entry.getKey(),
                        HtmlUtils.htmlEscape(entry.getValue() != null ? entry.getValue() : ""));
            body = variableResolver.resolve(body, safeVars);
        }

        String   number   = numberGen.next(tenantId);
        Contract contract = Contract.create(tenantId, number, req.title(),
                req.contractType(), body, req.templateId(), createdByUserId);

        if (req.startDate() != null || req.endDate() != null)
            contract.setDates(req.startDate(), req.endDate());
        if (req.valueAmount() != null) contract.setValueAmount(req.valueAmount());
        if (req.notes()       != null) contract.setNotes(req.notes());
        if (req.renewalNoticeDays() != null)
            contract.setAutoRenew(req.autoRenew(), req.renewalNoticeDays());

        contractRepo.save(contract);
        log.info("Created contract={} tenant={}", number, tenantId);
        return toContractResponse(contract);
    }

    /**
     * NEW: fills in remaining {{template}} variables (or edits the other
     * peripheral fields) on a contract that's still DRAFT or UNDER_REVIEW —
     * there was previously no way to do this at all once a contract was
     * created with blanks left in it (see ContractsTab.tsx's "Leave blank
     * to fill in later" flow), which meant sendForSigning()'s unresolved-
     * variable check was a dead end with no path back to actually finish
     * the contract short of deleting and recreating it.
     * <p>
     * Resolves variables against the contract's CURRENT body, not a
     * re-fetched template — c.getBody() already holds whatever {{tokens}}
     * are still unresolved (anything already filled at creation time was
     * already substituted and is no longer present as a token), so this
     * is exactly the same incremental-fill behavior createContract() uses,
     * just applied a second time. All other fields are optional partial
     * updates — only touched when actually provided.
     * <p>
     * Editability itself (DRAFT/UNDER_REVIEW only) is NOT checked here —
     * every mutator this calls (updateBody(), setDates(), etc.) already
     * self-guards via Contract.assertEditable(), consistent with this
     * codebase's existing convention of keeping status-transition rules on
     * the entity rather than the service.
     */
    @Transactional
    public ContractResponse updateContract(TenantId tenantId, UUID id, UpdateContractRequest req) {
        Contract c = findActive(tenantId, id);

        if (req.variables() != null && !req.variables().isEmpty()) {
            Map<String, String> safeVars = new HashMap<>();
            for (var entry : req.variables().entrySet())
                safeVars.put(entry.getKey(),
                        HtmlUtils.htmlEscape(entry.getValue() != null ? entry.getValue() : ""));
            String newBody = variableResolver.resolve(c.getBody() != null ? c.getBody() : "", safeVars);
            c.updateBody(newBody);
        }

        // setDates()/setAutoRenew() take both their params together, so a
        // partial update (only startDate provided, say) must be merged
        // against the existing value rather than nulling the other one out.
        if (req.startDate() != null || req.endDate() != null) {
            LocalDate newStart = req.startDate() != null ? req.startDate() : c.getStartDate();
            LocalDate newEnd    = req.endDate()   != null ? req.endDate()   : c.getEndDate();
            c.setDates(newStart, newEnd);
        }
        if (req.valueAmount() != null) c.setValueAmount(req.valueAmount());
        if (req.notes()       != null) c.setNotes(req.notes());
        if (req.autoRenew() != null || req.renewalNoticeDays() != null) {
            boolean newAutoRenew   = req.autoRenew() != null ? req.autoRenew() : c.isAutoRenew();
            int     newNoticeDays  = req.renewalNoticeDays() != null ? req.renewalNoticeDays() : c.getRenewalNoticeDays();
            c.setAutoRenew(newAutoRenew, newNoticeDays);
        }

        contractRepo.save(c);
        c.setParties(partyRepo.findByContract(id));
        log.info("Updated contract={} tenant={}", c.getContractNumber(), tenantId);
        return toContractResponse(c);
    }

    @Transactional
    public ContractResponse submitForReview(TenantId tenantId, UUID id) {
        Contract c = findActive(tenantId, id);
        c.submitForReview();
        contractRepo.save(c);
        c.setParties(partyRepo.findByContract(id));
        return toContractResponse(c);
    }

    @Transactional
    public ContractResponse sendForSigning(TenantId tenantId, UUID id) {
        Contract c = findActive(tenantId, id);
        List<ContractParty> parties = partyRepo.findByContract(id);
        c.setParties(parties);

        if (parties.isEmpty())
            throw new IllegalStateException("Add at least one party before sending for signing");

        // FIX §7: Use ContractVariableResolver.findUnresolved() instead of raw regex
        List<String> unresolved = variableResolver.findUnresolved(c.getBody() != null ? c.getBody() : "");
        if (!unresolved.isEmpty())
            throw new IllegalStateException(
                    "Contract has unresolved variables: " + String.join(", ", unresolved));

        // Lock body — SHA-256 hash prevents tamper after sending
        String bodyHash = signingTokenService.sha256(c.getBody() != null ? c.getBody() : "");
        c.lockBody(bodyHash);
        c.send();
        contractRepo.save(c);

        // Issue a signing token and send invitation per party
        for (ContractParty party : parties) {
            revokeActiveTokens(party.getId());

            String  token     = signingTokenService.generateToken(c.getId(), party.getId(), tenantId.getValue());
            String  signUrl   = baseUrl + "/sign/" + token;
            Instant expiresAt = Instant.now().plusSeconds(72L * 3600);

            ContractSigningToken st = ContractSigningToken.create(
                    tenantId.getValue(), c.getId(), party.getId(), token, expiresAt);
            signingTokenRepo.save(st);

            // FIX: previously also called party.setSigningToken(token) here,
            // persisting a second, unsynchronized raw copy of the same secret
            // on contract_parties — see ContractParty.java for the removed
            // field. The single source of truth for a party's active token is
            // now signingTokenRepo.findActiveByPartyId(...), used wherever the
            // raw token needs to be recovered later (e.g. notifyNextParty()).

            // Email invitation — async (EmailService is @Async)
            if (party.getEmail() != null && !party.getEmail().isBlank()) {
                emailService.send(
                        party.getEmail(),
                        "Action required: Please sign " + c.getTitle(),
                        EmailTemplates.contractSigningInvitation(
                                party.getFullName(),
                                c.getTitle(),
                                c.getContractNumber(),
                                c.getContractType().replace("_", " "),
                                signUrl));
                party.setEmailSentAt(Instant.now());
                partyRepo.save(party);
            }

            // FIX: SMS now actually called via SmsService
            // OTP is not sent here — parties use the signing link to request OTP themselves
            // SMS here is a notification that a signing link has been sent
            if (party.getPhone() != null && !party.getPhone().isBlank()) {
                String smsBody = "HandyFlow: You have been invited to sign \""
                        + truncate(c.getTitle(), 40)
                        + "\". Open: " + signUrl;
                safeSms(party.getPhone(), smsBody);
            }

            log.info("Signing token issued for party={} contract={}", party.getId(), c.getContractNumber());
        }

        log.info("Contract {} sent for signing — {} parties", c.getContractNumber(), parties.size());
        return toContractResponse(c);
    }

    @Transactional
    public ContractResponse terminate(TenantId tenantId, UUID id, String reason) {
        Contract c = findActive(tenantId, id);
        c.terminate(reason);
        contractRepo.save(c);
        c.setParties(partyRepo.findByContract(id));
        log.info("Contract {} terminated: {}", c.getContractNumber(), reason);
        // FIX: nobody was previously notified of a termination at all — see
        // sendTerminationNotifications()'s Javadoc.
        sendTerminationNotifications(c, reason);
        return toContractResponse(c);
    }

    // ── Resend signing link ────────────────────────────────────────────────────

    @Transactional
    public PartyResponse resendSigningLink(TenantId tenantId, UUID contractId, UUID partyId) {
        Contract c = findActive(tenantId, contractId);
        if (!"SENT".equals(c.getStatus()))
            throw new IllegalStateException("Contract must be SENT to resend a signing link");

        ContractParty party = partyRepo.findByTenantAndId(tenantId, partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));

        if ("SIGNED".equals(party.getSigningStatus()))
            throw new IllegalStateException(party.getFullName() + " has already signed this contract");
        if ("DECLINED".equals(party.getSigningStatus()))
            throw new IllegalStateException(party.getFullName() + " has declined. " +
                    "Withdraw the declination before resending.");

        revokeActiveTokens(partyId);

        String  token     = signingTokenService.generateToken(c.getId(), partyId, tenantId.getValue());
        String  signUrl   = baseUrl + "/sign/" + token;
        Instant expiresAt = Instant.now().plusSeconds(72L * 3600);

        ContractSigningToken st = ContractSigningToken.create(
                tenantId.getValue(), c.getId(), partyId, token, expiresAt);
        signingTokenRepo.save(st);

        // FIX: see the matching comment in sendForSigning() above — no longer
        // duplicating the raw token onto ContractParty.

        // Resend email
        if (party.getEmail() != null && !party.getEmail().isBlank()) {
            emailService.send(
                    party.getEmail(),
                    "Reminder: Please sign " + c.getTitle(),
                    EmailTemplates.contractSigningInvitation(
                            party.getFullName(),
                            c.getTitle(),
                            c.getContractNumber(),
                            c.getContractType().replace("_", " "),
                            signUrl));
            party.setEmailSentAt(Instant.now());
            partyRepo.save(party);
        }

        if (party.getPhone() != null && !party.getPhone().isBlank()) {
            safeSms(party.getPhone(),
                    "HandyFlow reminder: Sign \"" + truncate(c.getTitle(), 40)
                            + "\" at: " + signUrl);
        }

        log.info("Signing link resent for party={} contract={}", partyId, c.getContractNumber());
        return toPartyResponse(party);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Parties
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public PartyResponse addParty(TenantId tenantId, UUID contractId, AddPartyRequest req) {
        findActive(tenantId, contractId);
        ContractParty party = ContractParty.create(tenantId, contractId,
                req.partyType(), req.partyRole(), req.fullName(),
                req.email(), req.phone(), req.companyName(),
                req.signingOrder() != null ? req.signingOrder() : 1);
        partyRepo.save(party);
        return toPartyResponse(party);
    }

    @Transactional
    public PartyResponse requestOtp(TenantId tenantId, UUID contractId, UUID partyId) {
        Contract c = findActive(tenantId, contractId);
        List<ContractParty> parties = partyRepo.findByContract(contractId);
        c.setParties(parties);

        if (!"SENT".equals(c.getStatus()))
            throw new IllegalStateException("Contract must be SENT before OTP can be requested");

        ContractParty party = partyRepo.findByTenantAndId(tenantId, partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));
        if (party.getPhone() == null)
            throw new IllegalStateException("Party has no phone number — cannot send OTP");

        checkOtpRateLimit(partyId.toString());
        enforceSigningOrder(c, party);

        String otp = otpService.generateAndStore(partyId.toString());
        party.markOtpSent();
        partyRepo.save(party);

        // FIX: SMS now actually sent
        boolean sent = safeSms(party.getPhone(),
                EmailTemplates.otpSmsText(otp, c.getTitle()));
        if (!sent) {
            log.warn("SMS delivery failed for partyId={} — OTP still stored for retry", partyId);
        }

        log.info("OTP generated for partyId={} contractId={}", partyId, contractId);
        return toPartyResponse(party);
    }

    @Transactional
    public ContractResponse signContract(TenantId tenantId, UUID contractId,
                                         UUID partyId, SignContractRequest req,
                                         String ipAddress, String userAgent,
                                         UUID witnessedByUserId) {
        Contract c = findActive(tenantId, contractId);
        List<ContractParty> parties = partyRepo.findByContract(contractId);
        c.setParties(parties);

        ContractParty party = parties.stream()
                .filter(p -> p.getId().equals(partyId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));

        if (!otpService.verify(partyId.toString(), req.otpCode())) {
            recordOtpFailure(partyId.toString());
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        clearOtpFailures(partyId.toString());

        // FIX: this method already existed with the full correct logic
        // (OTP verification, signature recording, all-parties-signed check,
        // notifications) but was never actually called from anywhere —
        // ContractingController had no endpoint wired to it at all, despite
        // the frontend (ContractsTab.tsx) calling
        // POST /{id}/parties/{partyId}/sign expecting exactly this. Only
        // change needed here was threading witnessedByUserId through to the
        // audit trail — see ContractSignature.witnessedByUserId's Javadoc.
        recordSignature(tenantId, contractId, partyId, party, req, ipAddress, userAgent, witnessedByUserId);

        // Reload parties after signature
        parties = partyRepo.findByContract(contractId);
        c.setParties(parties);

        if (c.allPartiesSigned()) {
            c.markSigned();
            contractRepo.save(c);
            log.info("Contract {} fully signed", c.getContractNumber());
            sendFullyExecutedNotifications(c);
        } else {
            // Notify next party it's their turn (enforced signing order)
            notifyNextParty(c, parties);
        }

        return toContractResponse(c);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public signing (token-authenticated, no JWT)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public PublicContractView getPublicContractView(TenantId tenantId, UUID contractId,
                                                    UUID partyId, String token) {
        Contract c = findActive(tenantId, contractId);
        List<ContractParty> parties = partyRepo.findByContract(contractId);
        c.setParties(parties);

        ContractParty me = parties.stream()
                .filter(p -> p.getId().equals(partyId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));

        ContractSigningToken st = findValidToken(token);

        if (me.getViewedAt() == null) {
            me.setViewedAt(Instant.now());
            partyRepo.save(me);
        }

        List<OtherPartyView> others = parties.stream()
                .filter(p -> !p.getId().equals(partyId))
                .map(p -> new OtherPartyView(p.getFullName(), p.getPartyRole(),
                        p.getCompanyName(), p.getSigningOrder(),
                        p.getSigningStatus(), p.getSignedAt()))
                .toList();

        List<CommentView> comments = commentRepo.findByContract(contractId)
                .stream().map(comm -> toCommentViewWithParties(comm, parties)).toList();

        PublicPartyView myView = new PublicPartyView(
                me.getId(), me.getFullName(), me.getPartyRole(), me.getPartyType(),
                me.getCompanyName(), me.getEmail(), maskPhone(me.getPhone()),
                me.getSigningOrder(), me.getSigningStatus());

        return new PublicContractView(
                c.getId(), c.getContractNumber(), c.getTitle(), c.getContractType(),
                c.getStatus(), c.getBody(), c.getStartDate(), c.getEndDate(),
                c.getValueAmount(), c.getCurrency(), c.getNotes(),
                myView, others,
                "SIGNED".equals(me.getSigningStatus()), me.getSignedAt(),
                st.getExpiresAt(), c.getBodyHash(), comments);
    }

    @Transactional
    public void requestOtpByToken(TenantId tenantId, UUID contractId, UUID partyId) {
        Contract c = findActive(tenantId, contractId);
        List<ContractParty> parties = partyRepo.findByContract(contractId);
        c.setParties(parties);

        if (!"SENT".equals(c.getStatus()))
            throw new IllegalStateException("Contract is no longer open for signing");

        ContractParty party = parties.stream()
                .filter(p -> p.getId().equals(partyId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));

        if ("SIGNED".equals(party.getSigningStatus()))
            throw new IllegalStateException("You have already signed this contract");
        if ("DECLINED".equals(party.getSigningStatus()))
            throw new IllegalStateException("You have declined this contract. Contact the sender to restart.");
        if (party.getPhone() == null)
            throw new IllegalStateException("No phone number registered for you — contact the sender");

        checkOtpRateLimit(partyId.toString());
        enforceSigningOrder(c, party);

        String otp = otpService.generateAndStore(partyId.toString());
        party.markOtpSent();
        partyRepo.save(party);

        // FIX: SMS now sent
        boolean sent = safeSms(party.getPhone(),
                EmailTemplates.otpSmsText(otp, c.getTitle()));
        if (!sent) {
            log.warn("SMS delivery failed for partyId={} — check SMS provider config", partyId);
        }

        log.info("OTP issued via token flow for partyId={} contractId={}", partyId, contractId);
    }

    @Transactional
    public SigningResultView signByToken(TenantId tenantId, UUID contractId,
                                         UUID partyId, SignContractRequest req,
                                         String ipAddress, String userAgent, String token) {
        Contract c = findActive(tenantId, contractId);
        List<ContractParty> parties = partyRepo.findByContract(contractId);
        c.setParties(parties);

        ContractParty party = parties.stream()
                .filter(p -> p.getId().equals(partyId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));

        if ("SIGNED".equals(party.getSigningStatus()))
            throw new IllegalStateException("You have already signed this contract");

        ContractSigningToken st = findValidToken(token);

        if (!otpService.verify(partyId.toString(), req.otpCode())) {
            recordOtpFailure(partyId.toString());
            throw new IllegalArgumentException("Incorrect OTP. Please request a new one if this persists.");
        }
        clearOtpFailures(partyId.toString());

        recordSignature(tenantId, contractId, partyId, party, req, ipAddress, userAgent, null);

        // Mark token consumed — single-use
        st.setUsedAt(Instant.now());
        signingTokenRepo.save(st);

        // Reload
        parties = partyRepo.findByContract(contractId);
        c.setParties(parties);

        boolean fullyExecuted = false;
        if (c.allPartiesSigned()) {
            c.markSigned();
            contractRepo.save(c);
            fullyExecuted = true;
            log.info("Contract {} fully executed via public signing", c.getContractNumber());
            sendFullyExecutedNotifications(c);
        } else {
            notifyNextParty(c, parties);
        }

        return new SigningResultView(
                c.getId(), c.getContractNumber(), c.getTitle(), fullyExecuted,
                party.getFullName(), party.getSignedAt(),
                fullyExecuted
                        ? "All parties have signed. A confirmation has been emailed to all parties."
                        : "Your signature has been recorded. Awaiting the remaining parties.");
    }

    @Transactional
    public void declineByToken(TenantId tenantId, UUID contractId, UUID partyId, String reason) {
        Contract c = findActive(tenantId, contractId);
        List<ContractParty> parties = partyRepo.findByContract(contractId);

        ContractParty party = parties.stream()
                .filter(p -> p.getId().equals(partyId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));

        if ("SIGNED".equals(party.getSigningStatus()))
            throw new IllegalStateException("Cannot decline — you have already signed this contract");

        party.setSigningStatus("DECLINED");
        party.setDeclinedAt(Instant.now());
        party.setDeclineReason(reason);
        partyRepo.save(party);

        revokeActiveTokens(partyId);

        log.info("Party {} declined contract {} — reason: {}",
                party.getFullName(), c.getContractNumber(),
                reason != null ? reason : "(none)");

        // Notify owner by email
        emailService.send(
                ownerEmail,
                "Contract declined: " + c.getTitle(),
                EmailTemplates.contractDeclined(
                        ownerDisplayName, party.getFullName(),
                        c.getTitle(), c.getContractNumber(), reason, baseUrl));

        // FIX: this used to call smsService.send(ownerEmail, ...) — passing an
        // EMAIL ADDRESS as the phone number parameter, guaranteed to fail (or
        // worse, roll back this whole decline via the transaction-poisoning
        // bug fixed above) the moment a real SMS provider is active. There is
        // no owner phone number configured anywhere in this codebase
        // (application.yaml only has contracting.owner.name/email) — rather
        // than invent one, this is skipped with a clear log line. Add
        // contracting.owner.phone to application.yaml and inject it here via
        // @Value if owner SMS notifications are actually wanted; the email
        // notification above already covers this event correctly today.
        log.info("Owner SMS notification skipped for decline on contract={} — no owner phone number configured",
                c.getContractNumber());
    }

    @Transactional
    public CommentView addCommentByToken(TenantId tenantId, UUID contractId,
                                         UUID partyId, AddCommentRequest req) {
        findActive(tenantId, contractId);
        List<ContractParty> parties = partyRepo.findByContract(contractId);

        ContractParty party = parties.stream()
                .filter(p -> p.getId().equals(partyId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Party", partyId.toString()));

        ContractComment comment = ContractComment.create(
                tenantId.getValue(), contractId, partyId,
                req.comment(), req.clauseRef(), req.isAmendmentRequest());
        comment.setAuthorName(party.getFullName());
        comment.setAuthorRole(party.getPartyRole());
        commentRepo.save(comment);

        if (req.isAmendmentRequest()) {
            log.info("Amendment requested on contractId={} by={} clause={}",
                    contractId, party.getFullName(), req.clauseRef());

            contractRepo.findActiveById(tenantId, contractId).ifPresent(c ->
                    emailService.send(
                            ownerEmail,
                            "Amendment requested: " + c.getTitle(),
                            EmailTemplates.contractAmendmentRequested(
                                    ownerDisplayName, party.getFullName(),
                                    c.getTitle(), c.getContractNumber(),
                                    req.clauseRef(), req.comment(), baseUrl)));
        }

        return toCommentViewWithParties(comment, parties);
    }

    @Transactional
    public CommentView addCommentAsStaff(TenantId tenantId, UUID contractId,
                                         UUID postedByUserId, AddCommentRequest req) {
        findActive(tenantId, contractId);
        List<ContractParty> parties = partyRepo.findByContract(contractId);

        // partyId = null -> "posted by internal HandyFlow user", per
        // ContractComment's own existing Javadoc — this pathway already
        // existed at the entity/schema level, just had no service method or
        // controller endpoint actually using it.
        ContractComment comment = ContractComment.create(
                tenantId.getValue(), contractId, null,
                req.comment(), req.clauseRef(), req.isAmendmentRequest());
        comment.setPostedByUserId(postedByUserId);
        commentRepo.save(comment);

        if (req.isAmendmentRequest()) {
            log.info("Amendment requested (internal) on contractId={} by staff userId={} clause={}",
                    contractId, postedByUserId, req.clauseRef());

            contractRepo.findActiveById(tenantId, contractId).ifPresent(c ->
                    emailService.send(
                            ownerEmail,
                            "Amendment requested: " + c.getTitle(),
                            EmailTemplates.contractAmendmentRequested(
                                    ownerDisplayName, "Internal Team",
                                    c.getTitle(), c.getContractNumber(),
                                    req.clauseRef(), req.comment(), baseUrl)));
        }

        return toCommentViewWithParties(comment, parties);
    }

    @Transactional(readOnly = true)
    public List<CommentView> getCommentsByToken(TenantId tenantId, UUID contractId, UUID partyId) {
        List<ContractParty> parties = partyRepo.findByContract(contractId);
        boolean belongs = parties.stream().anyMatch(p -> p.getId().equals(partyId));
        if (!belongs)
            throw new ResourceNotFoundException("Party", partyId.toString());
        return commentRepo.findByContract(contractId).stream()
                .map(c -> toCommentViewWithParties(c, parties)).toList();
    }

    // ── Expiry scheduler (called by ContractExpiryScheduler) ─────────────────

    @Transactional
    public void expireContracts(TenantId tenantId) {
        contractRepo.findExpired(tenantId, java.time.LocalDate.now()).forEach(c -> {
            c.expire();
            contractRepo.save(c);
            log.info("Contract {} expired automatically", c.getContractNumber());
        });
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generatePdf(TenantId tenantId, UUID contractId) {
        Contract contract = findActive(tenantId, contractId);
        if (!"SIGNED".equals(contract.getStatus()) && !"TERMINATED".equals(contract.getStatus()))
            throw new IllegalStateException(
                    "PDF is only available for SIGNED or TERMINATED contracts. Status: "
                            + contract.getStatus());
        List<ContractParty> parties = partyRepo.findByContract(contractId);
        contract.setParties(parties);
        List<ContractSignature> signatures = signatureRepo.findByContract(contractId);
        return pdfGenerator.generate(contract, signatures, "HandyFlow Tenant", null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Contract findActive(TenantId tenantId, UUID id) {
        return contractRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    private ContractSigningToken findValidToken(String token) {
        // FIX: was signingTokenRepo.findByToken(token) — querying the raw
        // token column directly. Now hashes the incoming token and looks it
        // up by tokenHash instead, matching what V56's migration comment
        // said should happen (and what token_hash's NOT NULL constraint was
        // added for) but never actually did until now.
        String tokenHash = signingTokenService.sha256(token);
        ContractSigningToken st = signingTokenRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Signing link not found or already used"));
        if (st.getRevokedAt() != null)
            throw new IllegalArgumentException("This signing link has been revoked. Ask the sender to resend.");
        if (st.getUsedAt() != null)
            throw new IllegalArgumentException("This signing link has already been used.");
        return st;
    }

    /**
     * FIX §8: Enforces signing order — party N cannot request OTP until all parties
     * with a lower signingOrder have SIGNED status.
     */
    private void enforceSigningOrder(Contract c, ContractParty party) {
        int lowestUnsigned = c.getParties().stream()
                .filter(p -> !"SIGNED".equals(p.getSigningStatus())
                        && !"DECLINED".equals(p.getSigningStatus()))
                .mapToInt(ContractParty::getSigningOrder)
                .min()
                .orElse(Integer.MAX_VALUE);

        if (party.getSigningOrder() > lowestUnsigned) {
            throw new IllegalStateException(
                    "Another party (signing order " + lowestUnsigned + ") must sign before you. " +
                            "You will receive an SMS notification when it is your turn.");
        }
    }

    private void recordSignature(TenantId tenantId, UUID contractId, UUID partyId,
                                 ContractParty party, SignContractRequest req,
                                 String ipAddress, String userAgent, UUID witnessedByUserId) {
        String phone = party.getPhone() != null ? party.getPhone() : "";
        String last4 = phone.length() >= 4 ? phone.substring(phone.length() - 4) : phone;
        // FIX: hashOtp is called AFTER verify() consumed the OTP — we hash the submitted code
        // for audit purposes. The stored hash is for the audit trail, not re-verification.
        String otpHash = otpService.hashOtp(req.otpCode());

        ContractSignature sig = ContractSignature.create(tenantId, contractId, partyId,
                otpHash, last4, ipAddress, userAgent, req.signatureData(), witnessedByUserId);
        signatureRepo.save(sig);

        party.markSigned(ipAddress, userAgent);
        partyRepo.save(party);
    }

    private void revokeActiveTokens(UUID partyId) {
        signingTokenRepo.findAllActiveByPartyId(partyId).forEach(t -> {
            t.setRevokedAt(Instant.now());
            signingTokenRepo.save(t);
        });
    }

    /**
     * Notifies the next party (lowest signingOrder that is still PENDING/SENT)
     * that it is now their turn to sign.
     */
    private void notifyNextParty(Contract c, List<ContractParty> parties) {
        parties.stream()
                .filter(p -> "PENDING".equals(p.getSigningStatus()) || "SENT".equals(p.getSigningStatus()))
                .min(Comparator.comparingInt(ContractParty::getSigningOrder))
                .ifPresent(next -> {
                    // FIX: previously read next.getSigningToken() — the raw
                    // token duplicated onto ContractParty, now removed. The
                    // party was already issued a token when the contract was
                    // first sent for signing (sendForSigning() mints one for
                    // every party up front), so it should still be active here;
                    // the empty-fallback case would mean something inconsistent
                    // happened upstream and is logged rather than silently
                    // sending a broken link.
                    Optional<ContractSigningToken> activeToken = signingTokenRepo.findActiveByPartyId(next.getId());
                    if (activeToken.isEmpty()) {
                        log.warn("No active signing token found for next party={} contract={} — " +
                                        "cannot include a working signing link in their turn notification",
                                next.getId(), c.getContractNumber());
                    }
                    String signUrl = activeToken.map(t -> baseUrl + "/sign/" + t.getToken()).orElse(baseUrl);

                    if (next.getEmail() != null && !next.getEmail().isBlank()) {
                        emailService.send(
                                next.getEmail(),
                                "It's your turn to sign: " + c.getTitle(),
                                EmailTemplates.contractSigningTurnNotification(
                                        next.getFullName(), c.getTitle(),
                                        c.getContractNumber(),
                                        signUrl));
                    }
                    if (next.getPhone() != null && !next.getPhone().isBlank()) {
                        safeSms(next.getPhone(),
                                "HandyFlow: It's your turn to sign \""
                                        + truncate(c.getTitle(), 30) + "\". "
                                        + "Check your email for the signing link.");
                    }
                    log.info("Notified next signer partyId={} for contract={}",
                            next.getId(), c.getContractNumber());
                });
    }

    // FIX: PDF generation could theoretically throw (though generatePdf's own
    // status-gate check won't fire here since the contract is definitely
    // SIGNED/TERMINATED by the time this is called) — but this method runs
    // INSIDE the caller's transaction (signContract/signByToken/terminate),
    // not its own. An uncaught exception here would roll back a signing or
    // termination that had already genuinely succeeded, over something as
    // secondary as attaching a PDF to the notification about it — the same
    // "notification failure poisons the business transaction" class of bug
    // already fixed elsewhere this session (see safeSms). Returns null on
    // failure so callers can fall back to a plain email without an attachment
    // rather than losing the notification entirely.
    private byte[] tryGenerateContractPdf(Contract c) {
        try {
            List<ContractSignature> signatures = signatureRepo.findByContract(c.getId());
            return pdfGenerator.generate(c, signatures, "HandyFlow Tenant", null);
        } catch (Exception e) {
            log.error("Failed to generate PDF for contract={} — notification will be sent without attachment: {}",
                    c.getContractNumber(), e.getMessage(), e);
            return null;
        }
    }

    private void sendFullyExecutedNotifications(Contract c) {
        String signedAt = SAST_FMT.format(c.getSignedAt() != null ? c.getSignedAt() : Instant.now());

        // FIX: previously only linked back into the app ("View Signed
        // Contract") — that assumes every recipient has HandyFlow access,
        // which isn't true: external signers are frequently not tenants or
        // platform users at all, just a named party who received one signing
        // link. The PDF is now attached directly so everyone gets a durable
        // copy regardless of whether they can ever log in.
        String filename = c.getContractNumber() + ".pdf";
        byte[] pdf = tryGenerateContractPdf(c);

        for (ContractParty party : c.getParties()) {
            // Email all parties
            if (party.getEmail() != null && !party.getEmail().isBlank()) {
                String subject = "Contract fully executed: " + c.getTitle();
                String body = EmailTemplates.contractFullyExecuted(
                        party.getFullName(), c.getTitle(), c.getContractNumber(), signedAt, baseUrl);
                if (pdf != null) {
                    emailService.sendWithAttachment(party.getEmail(), subject, body, filename, pdf);
                } else {
                    emailService.send(party.getEmail(), subject, body);
                }
            }
            // SMS confirmation
            if (party.getPhone() != null && !party.getPhone().isBlank()) {
                safeSms(party.getPhone(),
                        "HandyFlow: \"" + truncate(c.getTitle(), 35)
                                + "\" has been fully signed by all parties. " + signedAt);
            }
        }
        // Also notify owner
        String ownerSubject = "Contract executed: " + c.getTitle();
        String ownerBody = EmailTemplates.contractFullyExecuted(ownerDisplayName, c.getTitle(),
                c.getContractNumber(), signedAt, baseUrl);
        if (pdf != null) {
            emailService.sendWithAttachment(ownerEmail, ownerSubject, ownerBody, filename, pdf);
        } else {
            emailService.send(ownerEmail, ownerSubject, ownerBody);
        }
    }

    // NEW: previously nobody was told when a SIGNED contract was terminated
    // at all — terminate() just flipped the status and returned, with zero
    // notification to any party or the owner. Mirrors
    // sendFullyExecutedNotifications()'s structure and the same defensive
    // PDF-generation handling.
    private void sendTerminationNotifications(Contract c, String reason) {
        String terminatedAt = SAST_FMT.format(c.getTerminatedAt() != null ? c.getTerminatedAt() : Instant.now());
        String filename = c.getContractNumber() + ".pdf";
        byte[] pdf = tryGenerateContractPdf(c);

        for (ContractParty party : c.getParties()) {
            if (party.getEmail() != null && !party.getEmail().isBlank()) {
                String subject = "Contract terminated: " + c.getTitle();
                String body = EmailTemplates.contractTerminated(
                        party.getFullName(), c.getTitle(), c.getContractNumber(),
                        reason, terminatedAt, baseUrl);
                if (pdf != null) {
                    emailService.sendWithAttachment(party.getEmail(), subject, body, filename, pdf);
                } else {
                    emailService.send(party.getEmail(), subject, body);
                }
            }
        }

        String ownerSubject = "Contract terminated: " + c.getTitle();
        String ownerBody = EmailTemplates.contractTerminated(ownerDisplayName, c.getTitle(),
                c.getContractNumber(), reason, terminatedAt, baseUrl);
        if (pdf != null) {
            emailService.sendWithAttachment(ownerEmail, ownerSubject, ownerBody, filename, pdf);
        } else {
            emailService.send(ownerEmail, ownerSubject, ownerBody);
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        String last4  = phone.substring(phone.length() - 4);
        String prefix = phone.length() > 7 ? phone.substring(0, phone.length() - 7) : "";
        return (prefix.isBlank() ? "" : prefix + " ") + "***" + last4;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ContractResponse toContractResponse(Contract c) {
        List<PartyResponse> partyResponses = c.getParties().stream()
                .map(this::toPartyResponse).toList();
        // NEW: comments were entirely missing from this response before —
        // see ContractResponse.comments' own comment for why. Fetched here
        // rather than requiring every one of this method's several call
        // sites to separately load and pass them in.
        List<ContractParty> parties = c.getParties();
        List<CommentView> comments = commentRepo.findByContract(c.getId()).stream()
                .map(comm -> toCommentViewWithParties(comm, parties)).toList();
        return new ContractResponse(
                c.getId(), c.getContractNumber(), c.getTemplateId(), c.getTitle(), c.getContractType(),
                c.getStatus(), c.getBody(), c.getBodyHash(),
                c.getValueAmount(), c.getCurrency(),
                c.getStartDate(), c.getEndDate(), c.isAutoRenew(), c.getRenewalNoticeDays(),
                c.getNotes(), c.getSentAt(), c.getSignedAt(),
                c.getTerminatedAt(), c.getTerminationReason(),
                partyResponses, comments, c.getCreatedAt(), c.getUpdatedAt());
    }

    private ContractSummaryResponse toContractSummary(Contract c, int total, int signed, int unresolvedAmendments) {
        return new ContractSummaryResponse(
                c.getId(), c.getContractNumber(), c.getTitle(), c.getContractType(),
                c.getStatus(), c.getValueAmount(), c.getCurrency(),
                c.getStartDate(), c.getEndDate(), c.isAutoRenew(),
                signed, total, unresolvedAmendments, c.getSentAt(), c.getSignedAt(), c.getCreatedAt());
    }

    private PartyResponse toPartyResponse(ContractParty p) {
        return new PartyResponse(p.getId(), p.getPartyType(), p.getPartyRole(),
                p.getFullName(), p.getEmail(), p.getPhone(), p.getCompanyName(),
                p.getSigningOrder(), p.getSigningStatus(), p.getSignedAt(), p.getOtpSentAt());
    }

    private TemplateResponse toTemplateResponse(ContractTemplate t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getContractType(),
                t.getDescription(), t.getBodyTemplate(), t.getVariables(),
                t.isSystem(), t.getCreatedAt());
    }

    private CommentView toCommentViewWithParties(ContractComment comm, List<ContractParty> parties) {
        String name = comm.getAuthorName();
        String role = comm.getAuthorRole();
        if ((name == null || role == null) && comm.getPartyId() != null) {
            parties.stream().filter(p -> p.getId().equals(comm.getPartyId()))
                    .findFirst().ifPresent(p -> {
                        comm.setAuthorName(p.getFullName());
                        comm.setAuthorRole(p.getPartyRole());
                    });
            name = comm.getAuthorName();
            role = comm.getAuthorRole();
        }
        if (name == null) name = "HandyFlow";
        if (role == null) role = "Internal";
        return new CommentView(comm.getId(), name, role,
                comm.isAmendmentRequest(), comm.getComment(), comm.getClauseRef(),
                comm.isResolved(), comm.getCreatedAt());
    }
}