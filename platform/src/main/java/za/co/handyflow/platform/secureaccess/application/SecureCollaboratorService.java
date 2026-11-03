package za.co.handyflow.platform.secureaccess.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.ReleasedFile;
import za.co.handyflow.platform.secureaccess.domain.model.*;
import za.co.handyflow.platform.secureaccess.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;
import za.co.handyflow.platform.shared.secureaccess.RecipientAccessRules;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * Registered collaborator accounts: repeat external users (consultants, contractors, client contacts) who
 * get ONE standing link and a workspace listing every share issued to their address, across modules.
 *
 * MODULE/STAFF SIDE: register, list, trail, reissue, disable (by default also cutting every share link
 * issued to them), enable again (with a new link).
 *
 * COLLABORATOR SIDE (public): the standing link is the credential but never enough alone. Order:
 * requestOtp -> verifyOtp (session in X-Recipient-Session) -> workspace / download / acknowledge.
 *
 * NOT A BYPASS: the workspace, downloads and acknowledgements all go through SecureShareService.viewOf /
 * downloadOf / acknowledgeOf, so every request is re-checked with the OWNING MODULE (which re-validates
 * the project, client or contract access and the document's current state) exactly as for a single link.
 * A collaborator only ever sees shares issued to them, and only the documents assigned to them.
 *
 * Like SecureShareService, public methods do their database work in a TransactionTemplate and throw AFTER
 * the commit so refusals are still logged.
 */
@Slf4j
@Service
public class SecureCollaboratorService {

    private static final int IP_MAX_REQUESTS  = 60;
    private static final long IP_WINDOW_MS    = 60_000L;
    private static final int OTP_MAX_PER_HOUR = 10;
    private static final long OTP_WINDOW_MS   = 3_600_000L;

    private final SecureCollaboratorRepository    collaboratorRepo;
    private final SecureCollaboratorLogRepository logRepo;
    private final SecureRecipientRepository       recipientRepo;
    private final SecureAccessLogRepository       shareLogRepo;
    private final SecureShareService              shares;
    private final SecureAccessMailer              mailer;
    private final RateLimiter                     rateLimiter;
    private final TransactionTemplate             tx;
    private final TransactionTemplate             newTx;

    public SecureCollaboratorService(SecureCollaboratorRepository collaboratorRepo,
                                     SecureCollaboratorLogRepository logRepo,
                                     SecureRecipientRepository recipientRepo,
                                     SecureAccessLogRepository shareLogRepo,
                                     SecureShareService shares, SecureAccessMailer mailer,
                                     RateLimiter rateLimiter, PlatformTransactionManager txManager) {
        this.collaboratorRepo = collaboratorRepo;
        this.logRepo          = logRepo;
        this.recipientRepo    = recipientRepo;
        this.shareLogRepo     = shareLogRepo;
        this.shares           = shares;
        this.mailer           = mailer;
        this.rateLimiter      = rateLimiter;
        this.tx               = new TransactionTemplate(txManager);
        this.newTx            = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ══ Staff side ════════════════════════════════════════════════════════════

    @Transactional
    public CollaboratorView register(UUID tenantId, RegisterCollaboratorRequest req, UUID by, String byName) {
        String email = RecipientAccessRules.normaliseEmail(req.email());
        if (collaboratorRepo.findByEmail(tenantId, email).isPresent()) {
            throw new HandyFlowException("An account for this address already exists. Enable or reissue it instead.",
                    HttpStatus.CONFLICT, "DUPLICATE_COLLABORATOR");
        }
        String token = RecipientAccessRules.newSecret();
        SecureCollaborator c = collaboratorRepo.save(SecureCollaborator.create(
                tenantId, req.name().trim(), blankToNull(req.organisation()), email,
                RecipientAccessRules.hashSecret(token), accountExpiry(req.expiryDays()), by, byName));
        int linked = recipientRepo.linkExisting(tenantId, email, c.getId());
        clog(c, "REGISTERED", "Link valid until " + c.getExpiresAt() + "; " + linked + " existing share(s) linked", null, byName);
        queueLink(c, token);
        return CollaboratorView.of(c, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<CollaboratorView> list(UUID tenantId) {
        Instant now = Instant.now();
        return collaboratorRepo.findByTenant(tenantId).stream().map(c -> CollaboratorView.of(c, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<CollaboratorLogEntry> trail(UUID tenantId, UUID id) {
        collaboratorRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> notFound("Collaborator"));
        return logRepo.findByCollaborator(tenantId, id).stream().map(CollaboratorLogEntry::of).toList();
    }

    /** A new standing link. The old link, any unused code and any open session stop working. */
    @Transactional
    public CollaboratorView reissue(UUID tenantId, UUID id, Integer expiryDays, String byName) {
        SecureCollaborator c = collaboratorRepo.findByTenantAndIdForUpdate(tenantId, id)
                .orElseThrow(() -> notFound("Collaborator"));
        if (c.isDisabled()) {
            throw new HandyFlowException("This account is disabled. Enable it to send a new link.",
                    HttpStatus.CONFLICT, "COLLABORATOR_DISABLED");
        }
        String token = RecipientAccessRules.newSecret();
        c.rotateToken(RecipientAccessRules.hashSecret(token), accountExpiry(expiryDays));
        collaboratorRepo.save(c);
        clog(c, "LINK_REISSUED", "New link issued; the previous link no longer works", null, byName);
        queueLink(c, token);
        return CollaboratorView.of(c, Instant.now());
    }

    /** Immediate. By default also revokes every share link issued to this person; pass revokeLinks=false to keep them. */
    @Transactional
    public CollaboratorView disable(UUID tenantId, UUID id, String reason, Boolean revokeLinks, String byName) {
        SecureCollaborator c = collaboratorRepo.findByTenantAndIdForUpdate(tenantId, id)
                .orElseThrow(() -> notFound("Collaborator"));
        Instant now = Instant.now();
        c.disable(byName, reason.trim(), now);
        collaboratorRepo.save(c);

        int revoked = 0;
        if (revokeLinks == null || revokeLinks) {
            for (SecureRecipient r : recipientRepo.findByCollaborator(tenantId, id)) {
                if (r.isRevoked()) continue;
                r.revoke(byName, "Collaborator account disabled: " + reason.trim(), now);
                recipientRepo.save(r);
                shareLogRepo.save(SecureAccessLog.of(r.getTenantId(), r.getShareId(), r.getId(), null, null,
                        "REVOKED", "Collaborator account disabled: " + reason.trim(), null, null, byName));
                revoked++;
            }
        }
        clog(c, "DISABLED", reason.trim() + "; " + revoked + " share link(s) revoked", null, byName);
        return CollaboratorView.of(c, now);
    }

    /** Turns the account back on with a NEW link (the old one may have been shared while it was off). */
    @Transactional
    public CollaboratorView enable(UUID tenantId, UUID id, Integer expiryDays, String byName) {
        SecureCollaborator c = collaboratorRepo.findByTenantAndIdForUpdate(tenantId, id)
                .orElseThrow(() -> notFound("Collaborator"));
        if (!c.isDisabled()) return CollaboratorView.of(c, Instant.now());
        String token = RecipientAccessRules.newSecret();
        c.enable(RecipientAccessRules.hashSecret(token), accountExpiry(expiryDays));
        collaboratorRepo.save(c);
        clog(c, "ENABLED", "Account enabled with a new link; share links revoked earlier stay revoked", null, byName);
        queueLink(c, token);
        return CollaboratorView.of(c, Instant.now());
    }

    // ══ Collaborator side (public) ════════════════════════════════════════════

    public OtpRequested requestOtp(String token, AccessCtx ctx) {
        throttleIp(ctx);
        if (!RecipientAccessRules.secretFormatOk(token)) throw notFoundPublic();
        String hash = RecipientAccessRules.hashSecret(token);
        if (!rateLimiter.tryConsume("sda:collab:otp:" + hash.substring(0, 24), OTP_MAX_PER_HOUR, OTP_WINDOW_MS)) {
            throw tooMany("Too many codes requested. Please try again later.");
        }
        Object[] res = this.<Object[]>inTx(() -> {
            AccessOutcome<SecureCollaborator> a = authoriseLink(hash, ctx, true);
            if (a.error != null) return AccessOutcome.fail(a.error);
            SecureCollaborator c = a.value;
            Instant now = Instant.now();
            if (!RecipientAccessRules.mayResendOtp(c.getOtpLastSentAt(), now)) {
                return AccessOutcome.fail(tooMany("A code was sent a moment ago. Please wait a minute before asking for another."));
            }
            String code = RecipientAccessRules.newOtp();
            c.issueOtp(RecipientAccessRules.hashOtp(c.getId(), code), now,
                    now.plus(Duration.ofMinutes(RecipientAccessRules.OTP_VALID_MINUTES)));
            collaboratorRepo.save(c);
            clog(c, "OTP_REQUESTED", "Code emailed to " + RecipientAccessRules.maskEmail(c.getEmail()), ctx, c.getName());
            return AccessOutcome.ok(new Object[]{c, code});
        });
        SecureCollaborator c = (SecureCollaborator) res[0];
        mailer.sendCollaboratorOtp(c, (String) res[1]);                         // after the commit
        return new OtpRequested(RecipientAccessRules.maskEmail(c.getEmail()), RecipientAccessRules.OTP_VALID_MINUTES);
    }

    public SessionGranted verifyOtp(String token, String code, AccessCtx ctx) {
        throttleIp(ctx);
        if (!RecipientAccessRules.secretFormatOk(token)) throw notFoundPublic();
        if (!RecipientAccessRules.otpFormatOk(code)) {
            throw new HandyFlowException("The code is six digits", HttpStatus.BAD_REQUEST, "INVALID_CODE");
        }
        String hash = RecipientAccessRules.hashSecret(token);
        return this.<SessionGranted>inTx(() -> {
            AccessOutcome<SecureCollaborator> a = authoriseLink(hash, ctx, true);
            if (a.error != null) return AccessOutcome.fail(a.error);
            SecureCollaborator c = a.value;
            Instant now = Instant.now();
            if (c.getOtpHash() == null || c.getOtpExpiresAt() == null || !c.getOtpExpiresAt().isAfter(now)) {
                clog(c, "OTP_FAILED", "No valid code (expired or never requested)", ctx, c.getName());
                return AccessOutcome.fail(new HandyFlowException("That code has expired. Request a new one.",
                        HttpStatus.UNPROCESSABLE_ENTITY, "CODE_EXPIRED"));
            }
            if (RecipientAccessRules.otpLocked(c.getOtpAttempts())) {
                clog(c, "OTP_LOCKED", "Too many wrong codes", ctx, c.getName());
                return AccessOutcome.fail(tooMany("Too many wrong codes. Request a new code."));
            }
            if (!RecipientAccessRules.hashesEqual(RecipientAccessRules.hashOtp(c.getId(), code), c.getOtpHash())) {
                c.registerOtpFailure();
                collaboratorRepo.save(c);
                clog(c, "OTP_FAILED", "Wrong code (attempt " + c.getOtpAttempts() + ")", ctx, c.getName());
                return AccessOutcome.fail(new HandyFlowException("That code is not correct",
                        HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CODE"));
            }
            String session = RecipientAccessRules.newSecret();
            Instant until = now.plus(Duration.ofMinutes(RecipientAccessRules.SESSION_MINUTES));
            c.startSession(RecipientAccessRules.hashSecret(session), until, now);
            collaboratorRepo.save(c);
            clog(c, "LOGIN", "Email address confirmed", ctx, c.getName());
            return AccessOutcome.ok(new SessionGranted(session, until));
        });
    }

    /** Every share still open to this person, each with only the documents assigned to them. */
    public Workspace workspace(String token, String session, AccessCtx ctx) {
        throttleIp(ctx);
        return this.<Workspace>inTx(() -> {
            AccessOutcome<SecureCollaborator> a = authorise(token, session, ctx);
            if (a.error != null) return AccessOutcome.fail(a.error);
            SecureCollaborator c = a.value;
            Instant now = Instant.now();
            List<WorkspaceEntry> entries = new ArrayList<>();
            for (SecureRecipient r : recipientRepo.findByCollaborator(c.getTenantId(), c.getId())) {
                if (!RecipientAccessRules.linkUsable(r.getExpiresAt(), r.getRevokedAt(), now)) continue;
                AccessOutcome<View> v = shares.viewOf(r, ctx);   // re-checks the owning module for every document
                if (v.error == null) entries.add(new WorkspaceEntry(r.getId(), v.value));
            }
            return AccessOutcome.ok(new Workspace(c.getName(), c.getOrganisation(), c.getExpiresAt(), entries));
        });
    }

    public ReleasedFile download(String token, String session, UUID recipientId, UUID documentId, AccessCtx ctx) {
        throttleIp(ctx);
        return this.<ReleasedFile>inTx(() -> {
            AccessOutcome<SecureCollaborator> a = authorise(token, session, ctx);
            if (a.error != null) return AccessOutcome.fail(a.error);
            SecureRecipient r = ownedUsable(a.value, recipientId, false);
            if (r == null) return AccessOutcome.fail(notFoundPublic());
            return shares.downloadOf(r, documentId, ctx);
        });
    }

    public Workspace acknowledge(String token, String session, UUID recipientId, String note, AccessCtx ctx) {
        throttleIp(ctx);
        this.<Boolean>inTx(() -> {
            AccessOutcome<SecureCollaborator> a = authorise(token, session, ctx);
            if (a.error != null) return AccessOutcome.fail(a.error);
            SecureRecipient r = ownedUsable(a.value, recipientId, true);
            if (r == null) return AccessOutcome.fail(notFoundPublic());
            return shares.acknowledgeOf(r, note, ctx);
        });
        return workspace(token, session, ctx);
    }

    // ══ Internals ═════════════════════════════════════════════════════════════

    /** A recipient row that belongs to this account and whose own link is still usable; otherwise null (a 404). */
    private SecureRecipient ownedUsable(SecureCollaborator c, UUID recipientId, boolean lock) {
        SecureRecipient r = (lock
                ? recipientRepo.findByCollaboratorAndIdForUpdate(c.getTenantId(), c.getId(), recipientId)
                : recipientRepo.findByCollaborator(c.getTenantId(), c.getId()).stream()
                        .filter(x -> x.getId().equals(recipientId)).findFirst())
                .orElse(null);
        if (r == null) return null;
        return RecipientAccessRules.linkUsable(r.getExpiresAt(), r.getRevokedAt(), Instant.now()) ? r : null;
    }

    private AccessOutcome<SecureCollaborator> authoriseLink(String hash, AccessCtx ctx, boolean lock) {
        Optional<SecureCollaborator> o = lock ? collaboratorRepo.findByTokenHashForUpdate(hash)
                                              : collaboratorRepo.findByTokenHash(hash);
        if (o.isEmpty()) return AccessOutcome.fail(notFoundPublic());
        SecureCollaborator c = o.get();
        if (c.isDisabled() || !c.getExpiresAt().isAfter(Instant.now())) {
            clog(c, "ACCESS_REFUSED", c.isDisabled() ? "Account disabled" : "Link expired", ctx, c.getName());
            return AccessOutcome.fail(new HandyFlowException(
                    "This link has expired or was withdrawn. Ask the sender to issue a new one.",
                    HttpStatus.GONE, "LINK_UNAVAILABLE"));
        }
        return AccessOutcome.ok(c);
    }

    private AccessOutcome<SecureCollaborator> authorise(String token, String session, AccessCtx ctx) {
        if (!RecipientAccessRules.secretFormatOk(token)) return AccessOutcome.fail(notFoundPublic());
        AccessOutcome<SecureCollaborator> a = authoriseLink(RecipientAccessRules.hashSecret(token), ctx, false);
        if (a.error != null) return a;
        SecureCollaborator c = a.value;
        boolean ok = session != null && RecipientAccessRules.secretFormatOk(session)
                && RecipientAccessRules.sessionUsable(c.getSessionExpiresAt(), Instant.now())
                && RecipientAccessRules.hashesEqual(RecipientAccessRules.hashSecret(session), c.getSessionHash());
        if (!ok) {
            if (session != null) clog(c, "ACCESS_REFUSED", "Session invalid or expired", ctx, c.getName());
            return AccessOutcome.fail(new HandyFlowException("Please confirm your email address to continue",
                    HttpStatus.UNAUTHORIZED, "VERIFICATION_REQUIRED"));
        }
        return a;
    }

    /** Sends only after the surrounding transaction commits, in its own transaction for the bookkeeping. */
    private void queueLink(SecureCollaborator c, String token) {
        Runnable send = () -> {
            try {
                boolean queued = mailer.sendCollaboratorLink(c, token);
                newTx.executeWithoutResult(s -> collaboratorRepo.findById(c.getId()).ifPresent(x -> {
                    if (queued) { x.markEmailSent(Instant.now()); collaboratorRepo.save(x); }
                    clog(x, queued ? "EMAIL_QUEUED" : "EMAIL_NOT_SENT",
                            queued ? "Link emailed to " + RecipientAccessRules.maskEmail(x.getEmail())
                                   : "Notifications are switched off; no email was sent", null, "System");
                }));
            } catch (RuntimeException e) {
                log.error("Could not queue collaborator email collaborator={}", c.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }

    private void clog(SecureCollaborator c, String action, String detail, AccessCtx ctx, String actor) {
        logRepo.save(SecureCollaboratorLog.of(c.getTenantId(), c.getId(), action, detail,
                ctx == null ? null : ctx.ip(), ctx == null ? null : ctx.userAgent(), actor));
    }

    private void throttleIp(AccessCtx ctx) {
        String ip = (ctx == null || ctx.ip() == null || ctx.ip().isBlank()) ? "unknown" : ctx.ip();
        if (!rateLimiter.tryConsume("sda:collab:ip:" + ip, IP_MAX_REQUESTS, IP_WINDOW_MS)) {
            throw tooMany("Too many requests — please try again shortly");
        }
    }

    private <T> T inTx(Supplier<AccessOutcome<T>> body) {
        AccessOutcome<T> out = tx.execute(status -> body.get());
        if (out == null) throw new IllegalStateException("Transaction returned no outcome");
        if (out.error != null) throw out.error;
        return out.value;
    }

    private static Instant accountExpiry(Integer days) {
        return Instant.now().plus(Duration.ofDays(RecipientAccessRules.clampAccountDays(days)));
    }

    private static HandyFlowException notFoundPublic() {
        return new HandyFlowException("Not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static HandyFlowException notFound(String what) {
        return new HandyFlowException(what + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static HandyFlowException tooMany(String message) {
        return new HandyFlowException(message, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
