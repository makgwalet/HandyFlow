package za.co.handyflow.platform.projects.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.projects.application.internal.TransmittalAccessService.Ctx;
import za.co.handyflow.platform.projects.domain.model.ProjectCollaborator;
import za.co.handyflow.platform.projects.domain.model.ProjectCollaboratorLog;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalAccessLog;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalRecipient;
import za.co.handyflow.platform.projects.domain.repository.ProjectCollaboratorLogRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectCollaboratorRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTransmittalAccessLogRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTransmittalRecipientRepository;
import za.co.handyflow.platform.projects.dto.CollaboratorPortalDtos;
import za.co.handyflow.platform.projects.dto.CollaboratorResponse;
import za.co.handyflow.platform.projects.dto.RecipientPortalDtos;
import za.co.handyflow.platform.projects.dto.RegisterCollaboratorRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.secureaccess.RecipientAccessRules;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Registered collaborator accounts: repeat external users (consultants, contractors, client contacts)
 * who get ONE standing link and a workspace listing every transmittal issued to their address.
 *
 * STAFF: register, list, send a fresh link, disable (and by default cut every transmittal link issued
 * to them), enable again (with a new link), read the account trail.
 *
 * COLLABORATOR (public path): the standing link is the credential but never enough alone. Order:
 * requestOtp -> verifyOtp (session in X-Recipient-Session) -> workspace / download / acknowledge.
 * Same limits as a single transmittal link (RecipientAccessRules).
 *
 * SCOPE: the workspace shows only recipient rows issued to this account that are still usable (not
 * revoked, not expired), and each shows only the documents assigned to that recipient. Downloads and
 * acknowledgements go through the SAME code as a single link (TransmittalAccessService.downloadOf and
 * acknowledgeOf), so the checks, integrity hash and audit trail cannot drift apart. A project-level
 * restriction therefore still applies: a collaborator never sees anything that was not issued to them.
 *
 * Like TransmittalAccessService, the public methods run their database work through a
 * TransactionTemplate and throw AFTER the commit, so refusals are still logged.
 */
@Slf4j
@Service
public class CollaboratorAccessService {

    private static final int IP_MAX_REQUESTS  = 60;
    private static final long IP_WINDOW_MS    = 60_000L;
    private static final int OTP_MAX_PER_HOUR = 10;
    private static final long OTP_WINDOW_MS   = 3_600_000L;

    private final ProjectCollaboratorRepository          collaboratorRepo;
    private final ProjectCollaboratorLogRepository       logRepo;
    private final ProjectTransmittalRecipientRepository  recipientRepo;
    private final ProjectTransmittalAccessLogRepository  transmittalLogRepo;
    private final TransmittalAccessService               access;
    private final TransmittalNotificationService         notifications;
    private final RateLimiter                            rateLimiter;
    private final TransactionTemplate                    tx;
    private final TransactionTemplate                    newTx;

    public CollaboratorAccessService(ProjectCollaboratorRepository collaboratorRepo,
                                     ProjectCollaboratorLogRepository logRepo,
                                     ProjectTransmittalRecipientRepository recipientRepo,
                                     ProjectTransmittalAccessLogRepository transmittalLogRepo,
                                     TransmittalAccessService access,
                                     TransmittalNotificationService notifications,
                                     RateLimiter rateLimiter,
                                     PlatformTransactionManager txManager) {
        this.collaboratorRepo   = collaboratorRepo;
        this.logRepo            = logRepo;
        this.recipientRepo      = recipientRepo;
        this.transmittalLogRepo = transmittalLogRepo;
        this.access             = access;
        this.notifications      = notifications;
        this.rateLimiter        = rateLimiter;
        this.tx                 = new TransactionTemplate(txManager);
        this.newTx              = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ══ Staff side ════════════════════════════════════════════════════════════

    @Transactional
    public CollaboratorResponse register(TenantId tenantId, RegisterCollaboratorRequest req, UUID by, String byName) {
        String email = RecipientAccessRules.normaliseEmail(req.email());
        if (collaboratorRepo.findByEmail(tenantId.getValue(), email).isPresent()) {
            throw new HandyFlowException("An account for this address already exists. Enable or reissue it instead.",
                    HttpStatus.CONFLICT, "DUPLICATE_COLLABORATOR");
        }
        String token = RecipientAccessRules.newSecret();
        ProjectCollaborator c = collaboratorRepo.save(ProjectCollaborator.create(
                tenantId.getValue(), req.name().trim(), blankToNull(req.organisation()), email,
                RecipientAccessRules.hashSecret(token), accountExpiry(req.expiryDays()), by, byName));
        int linked = recipientRepo.linkExisting(tenantId.getValue(), email, c.getId());
        clog(c, "REGISTERED", "Link valid until " + c.getExpiresAt() + "; " + linked
                + " existing transmittal(s) linked", null, byName);
        queueLink(c, token);
        return CollaboratorResponse.of(c, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<CollaboratorResponse> list(TenantId tenantId) {
        Instant now = Instant.now();
        return collaboratorRepo.findByTenant(tenantId.getValue()).stream().map(c -> CollaboratorResponse.of(c, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<CollaboratorResponse.LogEntry> trail(TenantId tenantId, UUID id) {
        collaboratorRepo.findByTenantAndId(tenantId.getValue(), id).orElseThrow(() -> notFound("Collaborator"));
        return logRepo.findByCollaborator(tenantId.getValue(), id).stream().map(CollaboratorResponse.LogEntry::of).toList();
    }

    /** A new standing link. The old link, any unused code and any open session stop working. */
    @Transactional
    public CollaboratorResponse reissue(TenantId tenantId, UUID id, Integer expiryDays, UUID by, String byName) {
        ProjectCollaborator c = collaboratorRepo.findByTenantAndIdForUpdate(tenantId.getValue(), id)
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
        return CollaboratorResponse.of(c, Instant.now());
    }

    /**
     * Immediate. By default also revokes every transmittal link issued to this person, so nothing they
     * already hold keeps working. Pass revokeTransmittalLinks=false to keep those individual links alive.
     */
    @Transactional
    public CollaboratorResponse disable(TenantId tenantId, UUID id, String reason, Boolean revokeTransmittalLinks,
                                        UUID by, String byName) {
        ProjectCollaborator c = collaboratorRepo.findByTenantAndIdForUpdate(tenantId.getValue(), id)
                .orElseThrow(() -> notFound("Collaborator"));
        Instant now = Instant.now();
        c.disable(byName, reason.trim(), now);
        collaboratorRepo.save(c);

        int revoked = 0;
        if (revokeTransmittalLinks == null || revokeTransmittalLinks) {
            for (ProjectTransmittalRecipient r : recipientRepo.findByCollaborator(tenantId.getValue(), id)) {
                if (r.isRevoked()) continue;
                r.revoke(byName, "Collaborator account disabled: " + reason.trim(), now);
                recipientRepo.save(r);
                transmittalLogRepo.save(ProjectTransmittalAccessLog.of(r.getTenantId(), r.getProjectId(), r.getTransmittalId(),
                        r.getId(), null, null, "REVOKED", "Collaborator account disabled: " + reason.trim(), null, null, byName));
                revoked++;
            }
        }
        clog(c, "DISABLED", reason.trim() + "; " + revoked + " transmittal link(s) revoked", null, byName);
        return CollaboratorResponse.of(c, now);
    }

    /** Turns the account back on with a NEW link (the old one may have been shared while it was off). */
    @Transactional
    public CollaboratorResponse enable(TenantId tenantId, UUID id, Integer expiryDays, UUID by, String byName) {
        ProjectCollaborator c = collaboratorRepo.findByTenantAndIdForUpdate(tenantId.getValue(), id)
                .orElseThrow(() -> notFound("Collaborator"));
        if (!c.isDisabled()) return CollaboratorResponse.of(c, Instant.now());
        String token = RecipientAccessRules.newSecret();
        c.enable(RecipientAccessRules.hashSecret(token), accountExpiry(expiryDays));
        collaboratorRepo.save(c);
        clog(c, "ENABLED", "Account enabled with a new link; transmittal links revoked earlier stay revoked", null, byName);
        queueLink(c, token);
        return CollaboratorResponse.of(c, Instant.now());
    }

    // ══ Collaborator side (public) ════════════════════════════════════════════

    public RecipientPortalDtos.OtpRequested requestOtp(String token, Ctx ctx) {
        throttleIp(ctx);
        if (!RecipientAccessRules.secretFormatOk(token)) throw notFoundPublic();
        String hash = RecipientAccessRules.hashSecret(token);
        if (!rateLimiter.tryConsume("projects:collab:otp:" + hash.substring(0, 24), OTP_MAX_PER_HOUR, OTP_WINDOW_MS)) {
            throw tooMany("Too many codes requested. Please try again later.");
        }
        Object[] res = this.<Object[]>inTx(() -> {
            AccessOutcome<ProjectCollaborator> a = authoriseLink(hash, ctx, true);
            if (a.error != null) return AccessOutcome.fail(a.error);
            ProjectCollaborator c = a.value;
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
        ProjectCollaborator c = (ProjectCollaborator) res[0];
        notifications.sendCollaboratorOtp(c, (String) res[1]);                  // after the commit
        return new RecipientPortalDtos.OtpRequested(RecipientAccessRules.maskEmail(c.getEmail()),
                RecipientAccessRules.OTP_VALID_MINUTES);
    }

    public RecipientPortalDtos.SessionGranted verifyOtp(String token, String code, Ctx ctx) {
        throttleIp(ctx);
        if (!RecipientAccessRules.secretFormatOk(token)) throw notFoundPublic();
        if (!RecipientAccessRules.otpFormatOk(code)) {
            throw new HandyFlowException("The code is six digits", HttpStatus.BAD_REQUEST, "INVALID_CODE");
        }
        String hash = RecipientAccessRules.hashSecret(token);
        return this.<RecipientPortalDtos.SessionGranted>inTx(() -> {
            AccessOutcome<ProjectCollaborator> a = authoriseLink(hash, ctx, true);
            if (a.error != null) return AccessOutcome.fail(a.error);
            ProjectCollaborator c = a.value;
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
            return AccessOutcome.ok(new RecipientPortalDtos.SessionGranted(session, until));
        });
    }

    /** Every transmittal still open to this person, each with only the documents assigned to them. */
    public CollaboratorPortalDtos.Workspace workspace(String token, String session, Ctx ctx) {
        throttleIp(ctx);
        return this.<CollaboratorPortalDtos.Workspace>inTx(() -> {
            AccessOutcome<ProjectCollaborator> a = authorise(token, session, ctx);
            if (a.error != null) return AccessOutcome.fail(a.error);
            ProjectCollaborator c = a.value;
            Instant now = Instant.now();
            List<CollaboratorPortalDtos.Entry> entries = new ArrayList<>();
            for (ProjectTransmittalRecipient r : recipientRepo.findByCollaborator(c.getTenantId(), c.getId())) {
                if (!RecipientAccessRules.linkUsable(r.getExpiresAt(), r.getRevokedAt(), now)) continue;
                AccessOutcome<RecipientPortalDtos.View> v = access.viewOf(r, ctx);
                if (v.error == null) entries.add(new CollaboratorPortalDtos.Entry(r.getId(), v.value));
            }
            return AccessOutcome.ok(new CollaboratorPortalDtos.Workspace(c.getName(), c.getOrganisation(), c.getExpiresAt(), entries));
        });
    }

    public DocumentService.FileDownload download(String token, String session, UUID recipientId, UUID documentId, Ctx ctx) {
        throttleIp(ctx);
        return this.<DocumentService.FileDownload>inTx(() -> {
            AccessOutcome<ProjectCollaborator> a = authorise(token, session, ctx);
            if (a.error != null) return AccessOutcome.fail(a.error);
            ProjectTransmittalRecipient r = ownedUsable(a.value, recipientId, false);
            if (r == null) return AccessOutcome.fail(notFoundPublic());
            return access.downloadOf(r, documentId, ctx);
        });
    }

    public CollaboratorPortalDtos.Workspace acknowledge(String token, String session, UUID recipientId, String note, Ctx ctx) {
        throttleIp(ctx);
        this.<Boolean>inTx(() -> {
            AccessOutcome<ProjectCollaborator> a = authorise(token, session, ctx);
            if (a.error != null) return AccessOutcome.fail(a.error);
            ProjectTransmittalRecipient r = ownedUsable(a.value, recipientId, true);
            if (r == null) return AccessOutcome.fail(notFoundPublic());
            return access.acknowledgeOf(r, note, ctx);
        });
        return workspace(token, session, ctx);
    }

    // ══ Internals ═════════════════════════════════════════════════════════════

    /** A recipient row that belongs to this account and whose own link is still usable; otherwise null (a 404). */
    private ProjectTransmittalRecipient ownedUsable(ProjectCollaborator c, UUID recipientId, boolean lock) {
        ProjectTransmittalRecipient r = (lock
                ? recipientRepo.findByCollaboratorAndIdForUpdate(c.getTenantId(), c.getId(), recipientId)
                : recipientRepo.findByCollaborator(c.getTenantId(), c.getId()).stream()
                        .filter(x -> x.getId().equals(recipientId)).findFirst())
                .orElse(null);
        if (r == null) return null;
        return RecipientAccessRules.linkUsable(r.getExpiresAt(), r.getRevokedAt(), Instant.now()) ? r : null;
    }

    private AccessOutcome<ProjectCollaborator> authoriseLink(String hash, Ctx ctx, boolean lock) {
        java.util.Optional<ProjectCollaborator> o = lock ? collaboratorRepo.findByTokenHashForUpdate(hash)
                                                         : collaboratorRepo.findByTokenHash(hash);
        if (o.isEmpty()) return AccessOutcome.fail(notFoundPublic());
        ProjectCollaborator c = o.get();
        if (c.isDisabled() || !c.getExpiresAt().isAfter(Instant.now())) {
            clog(c, "ACCESS_REFUSED", c.isDisabled() ? "Account disabled" : "Link expired", ctx, c.getName());
            return AccessOutcome.fail(new HandyFlowException(
                    "This link has expired or was withdrawn. Ask the sender to issue a new one.",
                    HttpStatus.GONE, "LINK_UNAVAILABLE"));
        }
        return AccessOutcome.ok(c);
    }

    private AccessOutcome<ProjectCollaborator> authorise(String token, String session, Ctx ctx) {
        if (!RecipientAccessRules.secretFormatOk(token)) return AccessOutcome.fail(notFoundPublic());
        AccessOutcome<ProjectCollaborator> a = authoriseLink(RecipientAccessRules.hashSecret(token), ctx, false);
        if (a.error != null) return a;
        ProjectCollaborator c = a.value;
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
    private void queueLink(ProjectCollaborator c, String token) {
        Runnable send = () -> {
            try {
                boolean queued = notifications.sendCollaboratorLink(c, null, token);
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

    private void clog(ProjectCollaborator c, String action, String detail, Ctx ctx, String actor) {
        logRepo.save(ProjectCollaboratorLog.of(c.getTenantId(), c.getId(), action, detail,
                ctx == null ? null : ctx.ip(), ctx == null ? null : ctx.userAgent(), actor));
    }

    private void throttleIp(Ctx ctx) {
        String ip = (ctx == null || ctx.ip() == null || ctx.ip().isBlank()) ? "unknown" : ctx.ip();
        if (!rateLimiter.tryConsume("projects:collab:ip:" + ip, IP_MAX_REQUESTS, IP_WINDOW_MS)) {
            throw tooMany("Too many requests — please try again shortly");
        }
    }

    private <T> T inTx(Supplier<AccessOutcome<T>> body) {
        AccessOutcome<T> out = tx.execute(status -> body.get());
        if (out == null) throw new IllegalStateException("Transaction returned no outcome");
        if (out.error != null) throw out.error;                  // thrown AFTER the commit, so the log rows persist
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
