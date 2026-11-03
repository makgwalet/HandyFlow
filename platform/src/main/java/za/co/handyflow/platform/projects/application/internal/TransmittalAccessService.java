package za.co.handyflow.platform.projects.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.projects.application.internal.document.RecipientAccessRules;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.*;
import za.co.handyflow.platform.projects.domain.repository.*;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Secure, per-recipient access to the documents on a transmittal.
 *
 * STAFF SIDE (authenticated, project-scoped): issue a link to each recipient, add a recipient later,
 * send a fresh link (the old one dies), revoke, and read the recipient list and the access log.
 *
 * RECIPIENT SIDE (public path; the link token is the credential, never enough on its own):
 *   1. requestOtp  — a six-digit code is emailed to the address on file
 *   2. verifyOtp   — a correct code starts a short session (a second secret, sent as X-Recipient-Session)
 *   3. view / download / acknowledge — each re-checks the link (not expired, not revoked), the session,
 *      the recipient's permission and that the document is one assigned to THIS recipient
 *
 * Because the code goes to the address on file, a forwarded link is useless to anyone who cannot read
 * that mailbox. Revoking, expiring or re-sending a link ends every open session at once.
 *
 * Every step is written to the append-only access log, including refusals. The public methods are NOT
 * @Transactional: they run their database work through a TransactionTemplate and decide the outcome
 * AFTER the commit, so a wrong code or a refusal is still recorded (a thrown exception inside the
 * transaction would have rolled the log row back).
 *
 * Unknown tokens, and tokens that do not look like tokens, all return the same 404. An expired or
 * revoked link returns 410 so a genuine recipient is told to ask for a new one.
 *
 * KNOWN LIMITS: a code can be requested up to 10 times an hour per link with 5 guesses each; MFA beyond
 * the emailed code and registered portal accounts for repeat collaborators are not built.
 */
@Slf4j
@Service
public class TransmittalAccessService {

    private static final int IP_MAX_REQUESTS     = 60;
    private static final long IP_WINDOW_MS       = 60_000L;
    private static final int OTP_MAX_PER_HOUR    = 10;
    private static final long OTP_WINDOW_MS      = 3_600_000L;

    private final ProjectRepository                       projectRepo;
    private final ProjectTransmittalRepository            transmittalRepo;
    private final ProjectTransmittalItemRepository        itemRepo;
    private final ProjectTransmittalRecipientRepository   recipientRepo;
    private final ProjectTransmittalRecipientItemRepository scopeRepo;
    private final ProjectTransmittalAccessLogRepository   logRepo;
    private final DocumentService                         documentService;
    private final TransmittalNotificationService          notifications;
    private final RateLimiter                             rateLimiter;
    private final TransactionTemplate                     tx;
    /** For work done in afterCommit: it must NOT join the transaction that has just committed. */
    private final TransactionTemplate                     newTx;

    public TransmittalAccessService(ProjectRepository projectRepo,
                                    ProjectTransmittalRepository transmittalRepo,
                                    ProjectTransmittalItemRepository itemRepo,
                                    ProjectTransmittalRecipientRepository recipientRepo,
                                    ProjectTransmittalRecipientItemRepository scopeRepo,
                                    ProjectTransmittalAccessLogRepository logRepo,
                                    DocumentService documentService,
                                    TransmittalNotificationService notifications,
                                    RateLimiter rateLimiter,
                                    PlatformTransactionManager txManager) {
        this.projectRepo     = projectRepo;
        this.transmittalRepo = transmittalRepo;
        this.itemRepo        = itemRepo;
        this.recipientRepo   = recipientRepo;
        this.scopeRepo       = scopeRepo;
        this.logRepo         = logRepo;
        this.documentService = documentService;
        this.notifications   = notifications;
        this.rateLimiter     = rateLimiter;
        this.tx              = new TransactionTemplate(txManager);
        this.newTx           = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Where a public request came from. Stored on every log row. */
    public record Ctx(String ip, String userAgent) {}

    // ══ Staff side ════════════════════════════════════════════════════════════

    /** Called by TransmittalService.create in the same transaction. */
    @Transactional
    public List<TransmittalRecipientResponse> issue(TenantId tenantId, Project project, ProjectTransmittal t,
                                                    List<ProjectTransmittalItem> items,
                                                    List<TransmittalRecipientRequest> specs,
                                                    Integer expiryDays, UUID by, String byName) {
        Set<String> emails = new HashSet<>();
        for (TransmittalRecipientRequest s : specs) {
            if (!emails.add(RecipientAccessRules.normaliseEmail(s.email()))) {
                throw new HandyFlowException("The same email address is listed twice: " + s.email(),
                        HttpStatus.BAD_REQUEST, "DUPLICATE_RECIPIENT");
            }
        }
        List<TransmittalRecipientResponse> out = new ArrayList<>();
        for (TransmittalRecipientRequest s : specs) {
            out.add(createRecipient(tenantId, project, t, items, s, expiryDays, by, byName));
        }
        return out;
    }

    /** Adds one more recipient to an existing transmittal. */
    @Transactional
    public TransmittalRecipientResponse addRecipient(TenantId tenantId, UUID projectId, UUID transmittalId,
                                                     TransmittalRecipientRequest spec, Integer expiryDays,
                                                     UUID by, String byName) {
        Project project = project(tenantId, projectId);
        if (project.getStatus() == ProjectStatus.CLOSED) {
            throw new HandyFlowException("Project is CLOSED — no further access can be granted",
                    HttpStatus.CONFLICT, "PROJECT_CLOSED");
        }
        ProjectTransmittal t = transmittal(tenantId, projectId, transmittalId);
        String email = RecipientAccessRules.normaliseEmail(spec.email());
        for (ProjectTransmittalRecipient existing : recipientRepo.findByTransmittal(tenantId.getValue(), transmittalId)) {
            if (!existing.isRevoked() && existing.getEmail().equals(email)) {
                throw new HandyFlowException("This address already has access to this transmittal",
                        HttpStatus.CONFLICT, "DUPLICATE_RECIPIENT");
            }
        }
        return createRecipient(tenantId, project, t, itemRepo.findByTransmittal(t.getId()), spec, expiryDays, by, byName);
    }

    /** A new link for the recipient. The old link, any unused code and any open session stop working. */
    @Transactional
    public TransmittalRecipientResponse reissue(TenantId tenantId, UUID projectId, UUID transmittalId, UUID recipientId,
                                                Integer expiryDays, UUID by, String byName) {
        Project project = project(tenantId, projectId);
        ProjectTransmittal t = transmittal(tenantId, projectId, transmittalId);
        ProjectTransmittalRecipient r = recipientRepo.findByTransmittalAndIdForUpdate(tenantId.getValue(), transmittalId, recipientId)
                .orElseThrow(() -> notFound("Recipient"));
        if (r.isRevoked()) {
            throw new HandyFlowException("Access for this recipient was revoked. Add them again to give new access.",
                    HttpStatus.CONFLICT, "RECIPIENT_REVOKED");
        }
        String token = RecipientAccessRules.newSecret();
        r.rotateToken(RecipientAccessRules.hashSecret(token), expiry(expiryDays));
        recipientRepo.save(r);
        log(r, null, null, "LINK_REISSUED", "New link issued; the previous link no longer works", null, byName);
        queueEmail(t, r, token, visibleItems(r), project.getName());
        return view(r);
    }

    /** Immediate: the link stops working at once and any open session ends. Sender or document controller only. */
    @Transactional
    public TransmittalRecipientResponse revoke(TenantId tenantId, UUID projectId, UUID transmittalId, UUID recipientId,
                                               String reason, UUID by, String byName) {
        project(tenantId, projectId);
        ProjectTransmittal t = transmittal(tenantId, projectId, transmittalId);
        boolean sender = by != null && by.equals(t.getSentBy());
        if (!sender && !documentService.isDocumentController(tenantId, projectId, by)) {
            throw new HandyFlowException("Only the person who sent the transmittal, or a document controller, can revoke access",
                    HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        ProjectTransmittalRecipient r = recipientRepo.findByTransmittalAndIdForUpdate(tenantId.getValue(), transmittalId, recipientId)
                .orElseThrow(() -> notFound("Recipient"));
        if (!r.isRevoked()) {
            r.revoke(byName, reason.trim(), Instant.now());
            recipientRepo.save(r);
            log(r, null, null, "REVOKED", reason.trim(), null, byName);
        }
        return view(r);
    }

    @Transactional(readOnly = true)
    public List<TransmittalRecipientResponse> recipients(TenantId tenantId, UUID projectId, UUID transmittalId) {
        project(tenantId, projectId);
        transmittal(tenantId, projectId, transmittalId);
        return recipientViews(tenantId, transmittalId);
    }

    @Transactional(readOnly = true)
    public List<TransmittalAccessLogResponse> accessLog(TenantId tenantId, UUID projectId, UUID transmittalId) {
        project(tenantId, projectId);
        transmittal(tenantId, projectId, transmittalId);
        return logRepo.findByTransmittal(tenantId.getValue(), transmittalId).stream()
                .map(TransmittalAccessLogResponse::of).toList();
    }

    /** Used by TransmittalService for the register views. */
    @Transactional(readOnly = true)
    public List<TransmittalRecipientResponse> recipientViews(TenantId tenantId, UUID transmittalId) {
        Instant now = Instant.now();
        return recipientRepo.findByTransmittal(tenantId.getValue(), transmittalId).stream()
                .map(r -> TransmittalRecipientResponse.of(r, scopeRepo.findItemIds(r.getId()), now)).toList();
    }

    // ══ Recipient side (public) ═══════════════════════════════════════════════

    /** Step 1: email a six-digit code to the address on file. The reply never reveals more than a masked address. */
    public RecipientPortalDtos.OtpRequested requestOtp(String token, Ctx ctx) {
        throttleIp(ctx);
        if (!RecipientAccessRules.secretFormatOk(token)) throw notFoundPublic();
        String hash = RecipientAccessRules.hashSecret(token);
        if (!rateLimiter.tryConsume("projects:transmittal:otp:" + hash.substring(0, 24), OTP_MAX_PER_HOUR, OTP_WINDOW_MS)) {
            throw tooMany("Too many codes requested. Please try again later.");
        }
        Object[] res = this.<Object[]>inTx(() -> {
            Out<ProjectTransmittalRecipient> a = authoriseLink(hash, ctx, true);
            if (a.error != null) return Out.fail(a.error);
            ProjectTransmittalRecipient r = a.value;
            Instant now = Instant.now();
            if (!RecipientAccessRules.mayResendOtp(r.getOtpLastSentAt(), now)) {
                return Out.fail(tooMany("A code was sent a moment ago. Please wait a minute before asking for another."));
            }
            String code = RecipientAccessRules.newOtp();
            r.issueOtp(RecipientAccessRules.hashOtp(r.getId(), code), now,
                    now.plus(Duration.ofMinutes(RecipientAccessRules.OTP_VALID_MINUTES)));
            recipientRepo.save(r);
            log(r, null, null, "OTP_REQUESTED", "Code emailed to " + RecipientAccessRules.maskEmail(r.getEmail()), ctx, r.getName());
            String number = transmittalRepo.findByProjectAndId(r.getTenantId(), r.getProjectId(), r.getTransmittalId())
                    .map(ProjectTransmittal::getTransmittalNumber).orElse("your transmittal");
            return Out.ok(new Object[]{r, code, number});
        });
        ProjectTransmittalRecipient r = (ProjectTransmittalRecipient) res[0];
        notifications.sendOtp(r, (String) res[1], (String) res[2]);          // after the commit
        return new RecipientPortalDtos.OtpRequested(RecipientAccessRules.maskEmail(r.getEmail()),
                RecipientAccessRules.OTP_VALID_MINUTES);
    }

    /** Step 2: a correct code starts a session. Wrong guesses are counted and the code locks after five. */
    public RecipientPortalDtos.SessionGranted verifyOtp(String token, String code, Ctx ctx) {
        throttleIp(ctx);
        if (!RecipientAccessRules.secretFormatOk(token)) throw notFoundPublic();
        if (!RecipientAccessRules.otpFormatOk(code)) {
            throw new HandyFlowException("The code is six digits", HttpStatus.BAD_REQUEST, "INVALID_CODE");
        }
        String hash = RecipientAccessRules.hashSecret(token);
        return this.<RecipientPortalDtos.SessionGranted>inTx(() -> {
            Out<ProjectTransmittalRecipient> a = authoriseLink(hash, ctx, true);
            if (a.error != null) return Out.fail(a.error);
            ProjectTransmittalRecipient r = a.value;
            Instant now = Instant.now();
            if (r.getOtpHash() == null || r.getOtpExpiresAt() == null || !r.getOtpExpiresAt().isAfter(now)) {
                log(r, null, null, "OTP_FAILED", "No valid code (expired or never requested)", ctx, r.getName());
                return Out.fail(new HandyFlowException("That code has expired. Request a new one.",
                        HttpStatus.UNPROCESSABLE_ENTITY, "CODE_EXPIRED"));
            }
            if (RecipientAccessRules.otpLocked(r.getOtpAttempts())) {
                log(r, null, null, "OTP_LOCKED", "Too many wrong codes", ctx, r.getName());
                return Out.fail(tooMany("Too many wrong codes. Request a new code."));
            }
            if (!RecipientAccessRules.hashesEqual(RecipientAccessRules.hashOtp(r.getId(), code), r.getOtpHash())) {
                r.registerOtpFailure();
                recipientRepo.save(r);
                log(r, null, null, "OTP_FAILED", "Wrong code (attempt " + r.getOtpAttempts() + ")", ctx, r.getName());
                return Out.fail(new HandyFlowException("That code is not correct", HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CODE"));
            }
            String session = RecipientAccessRules.newSecret();
            Instant until = now.plus(Duration.ofMinutes(RecipientAccessRules.SESSION_MINUTES));
            r.startSession(RecipientAccessRules.hashSecret(session), until, now);
            recipientRepo.save(r);
            log(r, null, null, "OTP_VERIFIED", "Email address confirmed", ctx, r.getName());
            return Out.ok(new RecipientPortalDtos.SessionGranted(session, until));
        });
    }

    /** What this recipient may see: only the documents assigned to them. */
    public RecipientPortalDtos.View view(String token, String session, Ctx ctx) {
        throttleIp(ctx);
        return this.<RecipientPortalDtos.View>inTx(() -> {
            Out<ProjectTransmittalRecipient> a = authorise(token, session, ctx, false);
            if (a.error != null) return Out.fail(a.error);
            ProjectTransmittalRecipient r = a.value;
            r.touch(Instant.now());
            recipientRepo.save(r);
            ProjectTransmittal t = transmittalRepo.findByProjectAndId(r.getTenantId(), r.getProjectId(), r.getTransmittalId())
                    .orElse(null);
            if (t == null) return Out.fail(notFoundPublic());
            String projectName = projectRepo.findByTenantAndId(r.getTenantId(), r.getProjectId())
                    .map(Project::getName).orElse("");
            List<RecipientPortalDtos.Document> docs = visibleItems(r).stream()
                    .map(i -> new RecipientPortalDtos.Document(i.getDocumentId(), i.getDocumentNumber(), i.getTitle(),
                            i.getRevision(), i.getStatusAtSend(), i.getFileName(), i.getFileSha256(),
                            r.isCanDownload() && i.getFileSha256() != null))
                    .toList();
            log(r, null, null, "VIEWED", docs.size() + " document(s) listed", ctx, r.getName());
            return Out.ok(new RecipientPortalDtos.View(t.getTransmittalNumber(), t.getSubject(), projectName,
                    t.getPurpose(), t.getNotes(), t.getSentByName(), t.getSentAt(), r.getName(), r.getExpiresAt(),
                    r.isCanDownload(), r.isCanAcknowledge(), r.getAcknowledgedAt(), docs));
        });
    }

    /** The exact revision that was issued, only if it is assigned to this recipient and they may download. */
    public DocumentService.FileDownload download(String token, String session, UUID documentId, Ctx ctx) {
        throttleIp(ctx);
        return this.<DocumentService.FileDownload>inTx(() -> {
            Out<ProjectTransmittalRecipient> a = authorise(token, session, ctx, false);
            if (a.error != null) return Out.fail(a.error);
            ProjectTransmittalRecipient r = a.value;
            if (!r.isCanDownload()) {
                log(r, documentId, null, "ACCESS_REFUSED", "Download not permitted for this recipient", ctx, r.getName());
                return Out.fail(new HandyFlowException("You can view this transmittal but not download its files",
                        HttpStatus.FORBIDDEN, "DOWNLOAD_NOT_PERMITTED"));
            }
            ProjectTransmittalItem item = visibleItems(r).stream()
                    .filter(i -> i.getDocumentId().equals(documentId)).findFirst().orElse(null);
            if (item == null) {                                   // not on the transmittal, or not assigned to this recipient
                log(r, documentId, null, "ACCESS_REFUSED", "Document not assigned to this recipient", ctx, r.getName());
                return Out.fail(notFoundPublic());
            }
            try {
                DocumentService.FileDownload file = documentService.downloadIssuedRevision(
                        r.getTenantId(), r.getProjectId(), documentId, item.getFileSha256(),
                        r.getName() + " <" + r.getEmail() + ">");
                r.touch(Instant.now());
                recipientRepo.save(r);
                log(r, documentId, item.getRevision(), "DOWNLOADED", item.getFileName(), ctx, r.getName());
                return Out.ok(file);
            } catch (HandyFlowException e) {
                log(r, documentId, item.getRevision(), "DOWNLOAD_FAILED", e.getMessage(), ctx, r.getName());
                return Out.fail(e);
            }
        });
    }

    /** Recorded once per recipient. When every active recipient has acknowledged, the transmittal itself is acknowledged. */
    public RecipientPortalDtos.View acknowledge(String token, String session, String note, Ctx ctx) {
        throttleIp(ctx);
        this.<Boolean>inTx(() -> {
            Out<ProjectTransmittalRecipient> a = authorise(token, session, ctx, true);
            if (a.error != null) return Out.fail(a.error);
            ProjectTransmittalRecipient r = a.value;
            if (!r.isCanAcknowledge()) {
                log(r, null, null, "ACCESS_REFUSED", "Acknowledgement not permitted for this recipient", ctx, r.getName());
                return Out.fail(new HandyFlowException("You are not able to acknowledge this transmittal",
                        HttpStatus.FORBIDDEN, "ACK_NOT_PERMITTED"));
            }
            if (r.getAcknowledgedAt() != null) {
                return Out.fail(new HandyFlowException("You have already acknowledged this transmittal",
                        HttpStatus.CONFLICT, "ALREADY_ACKNOWLEDGED"));
            }
            r.acknowledge(note == null || note.isBlank() ? null : note.trim(), Instant.now());
            recipientRepo.save(r);
            log(r, null, null, "ACKNOWLEDGED", note, ctx, r.getName());

            List<ProjectTransmittalRecipient> active = recipientRepo.findByTransmittal(r.getTenantId(), r.getTransmittalId())
                    .stream().filter(x -> !x.isRevoked()).toList();
            if (active.stream().allMatch(x -> x.getAcknowledgedAt() != null)) {
                transmittalRepo.findByProjectAndIdForUpdate(r.getTenantId(), r.getProjectId(), r.getTransmittalId()).ifPresent(t -> {
                    if (t.getAcknowledgedAt() == null) {
                        t.acknowledge(active.size() == 1 ? r.getName() : "All " + active.size() + " recipients");
                        transmittalRepo.save(t);
                    }
                });
            }
            return Out.ok(Boolean.TRUE);
        });
        return view(token, session, ctx);
    }

    // ══ Internals ═════════════════════════════════════════════════════════════

    private TransmittalRecipientResponse createRecipient(TenantId tenantId, Project project, ProjectTransmittal t,
                                                         List<ProjectTransmittalItem> items, TransmittalRecipientRequest s,
                                                         Integer expiryDays, UUID by, String byName) {
        Map<UUID, ProjectTransmittalItem> byDocument = items.stream()
                .collect(Collectors.toMap(ProjectTransmittalItem::getDocumentId, i -> i, (a, b) -> a));
        List<ProjectTransmittalItem> scoped = new ArrayList<>();
        if (s.documentIds() != null && !s.documentIds().isEmpty()) {
            for (UUID id : new LinkedHashSet<>(s.documentIds())) {
                ProjectTransmittalItem i = byDocument.get(id);
                if (i == null) {
                    throw new HandyFlowException("A document assigned to " + s.name() + " is not on this transmittal",
                            HttpStatus.BAD_REQUEST, "DOCUMENT_NOT_ON_TRANSMITTAL");
                }
                scoped.add(i);
            }
        }
        boolean all = scoped.isEmpty() || scoped.size() == items.size();

        String token = RecipientAccessRules.newSecret();
        ProjectTransmittalRecipient r = recipientRepo.save(ProjectTransmittalRecipient.create(
                tenantId.getValue(), project.getId(), t.getId(), s.name().trim(), blankToNull(s.organisation()),
                RecipientAccessRules.normaliseEmail(s.email()), RecipientAccessRules.hashSecret(token),
                s.canDownload() == null || s.canDownload(), s.canAcknowledge() == null || s.canAcknowledge(),
                expiry(expiryDays), by, byName));
        if (!all) {
            scopeRepo.saveAll(scoped.stream().map(i -> ProjectTransmittalRecipientItem.of(r.getId(), i.getId())).toList());
        }
        log(r, null, null, "LINK_ISSUED", "Link valid until " + r.getExpiresAt()
                + (all ? ", all documents" : ", " + scoped.size() + " document(s)"), null, byName);
        queueEmail(t, r, token, all ? items : scoped, project.getName());
        return TransmittalRecipientResponse.of(r, all ? List.of() : scoped.stream().map(ProjectTransmittalItem::getId).toList(), Instant.now());
    }

    /** Sends only after the surrounding transaction commits, so a rolled-back transmittal never emails a dead link. */
    private void queueEmail(ProjectTransmittal t, ProjectTransmittalRecipient r, String token,
                            List<ProjectTransmittalItem> items, String projectName) {
        Runnable send = () -> {
            try {
                boolean queued = notifications.sendTransmittal(t, r, token, items, projectName);
                newTx.executeWithoutResult(s -> {
                    recipientRepo.findById(r.getId()).ifPresent(x -> {
                        if (queued) { x.markEmailSent(Instant.now()); recipientRepo.save(x); }
                        log(x, null, null, queued ? "EMAIL_QUEUED" : "EMAIL_NOT_SENT",
                                queued ? "Link emailed to " + RecipientAccessRules.maskEmail(x.getEmail())
                                       : "Notifications are switched off; no email was sent", null, "System");
                    });
                });
            } catch (RuntimeException e) {
                log.error("Could not queue transmittal email recipient={}", r.getId(), e);
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

    private List<ProjectTransmittalItem> visibleItems(ProjectTransmittalRecipient r) {
        List<ProjectTransmittalItem> all = itemRepo.findByTransmittal(r.getTransmittalId());
        Set<UUID> scope = new HashSet<>(scopeRepo.findItemIds(r.getId()));
        return scope.isEmpty() ? all : all.stream().filter(i -> scope.contains(i.getId())).toList();
    }

    /** Link checks only: format, known, not expired, not revoked. Used before a session exists. */
    private Out<ProjectTransmittalRecipient> authoriseLink(String hash, Ctx ctx, boolean lock) {
        Optional<ProjectTransmittalRecipient> o = lock ? recipientRepo.findByTokenHashForUpdate(hash) : recipientRepo.findByTokenHash(hash);
        if (o.isEmpty()) return Out.fail(notFoundPublic());
        ProjectTransmittalRecipient r = o.get();
        if (!RecipientAccessRules.linkUsable(r.getExpiresAt(), r.getRevokedAt(), Instant.now())) {
            log(r, null, null, "ACCESS_REFUSED", r.isRevoked() ? "Link revoked" : "Link expired", ctx, r.getName());
            return Out.fail(new HandyFlowException("This link has expired or was withdrawn. Ask the sender to issue a new one.",
                    HttpStatus.GONE, "LINK_UNAVAILABLE"));
        }
        return Out.ok(r);
    }

    /** Link AND session. */
    private Out<ProjectTransmittalRecipient> authorise(String token, String session, Ctx ctx, boolean lock) {
        if (!RecipientAccessRules.secretFormatOk(token)) return Out.fail(notFoundPublic());
        Out<ProjectTransmittalRecipient> a = authoriseLink(RecipientAccessRules.hashSecret(token), ctx, lock);
        if (a.error != null) return a;
        ProjectTransmittalRecipient r = a.value;
        boolean ok = session != null && RecipientAccessRules.secretFormatOk(session)
                && RecipientAccessRules.sessionUsable(r.getSessionExpiresAt(), Instant.now())
                && RecipientAccessRules.hashesEqual(RecipientAccessRules.hashSecret(session), r.getSessionHash());
        if (!ok) {
            if (session != null) log(r, null, null, "ACCESS_REFUSED", "Session invalid or expired", ctx, r.getName());
            return Out.fail(new HandyFlowException("Please confirm your email address to continue",
                    HttpStatus.UNAUTHORIZED, "VERIFICATION_REQUIRED"));
        }
        return Out.ok(r);
    }

    private void log(ProjectTransmittalRecipient r, UUID documentId, String revision, String action, String detail,
                     Ctx ctx, String actor) {
        logRepo.save(ProjectTransmittalAccessLog.of(r.getTenantId(), r.getProjectId(), r.getTransmittalId(), r.getId(),
                documentId, revision, action, detail, ctx == null ? null : ctx.ip(), ctx == null ? null : ctx.userAgent(), actor));
    }

    private void throttleIp(Ctx ctx) {
        String ip = (ctx == null || ctx.ip() == null || ctx.ip().isBlank()) ? "unknown" : ctx.ip();
        if (!rateLimiter.tryConsume("projects:transmittal:ip:" + ip, IP_MAX_REQUESTS, IP_WINDOW_MS)) {
            throw tooMany("Too many requests — please try again shortly");
        }
    }

    private static Instant expiry(Integer days) {
        return Instant.now().plus(Duration.ofDays(RecipientAccessRules.clampExpiryDays(days)));
    }

    private TransmittalRecipientResponse view(ProjectTransmittalRecipient r) {
        return TransmittalRecipientResponse.of(r, scopeRepo.findItemIds(r.getId()), Instant.now());
    }

    private Project project(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId).orElseThrow(() -> notFound("Project"));
    }

    private ProjectTransmittal transmittal(TenantId tenantId, UUID projectId, UUID id) {
        return transmittalRepo.findByProjectAndId(tenantId.getValue(), projectId, id).orElseThrow(() -> notFound("Transmittal"));
    }

    // Outcome of a transaction body: a value, or the error to throw once the transaction has committed.
    private static final class Out<T> {
        final T value;
        final HandyFlowException error;
        private Out(T value, HandyFlowException error) { this.value = value; this.error = error; }
        static <T> Out<T> ok(T v) { return new Out<>(v, null); }
        static <T> Out<T> fail(HandyFlowException e) { return new Out<>(null, e); }
    }

    private <T> T inTx(Supplier<Out<T>> body) {
        Out<T> out = tx.execute(status -> body.get());
        if (out == null) throw new IllegalStateException("Transaction returned no outcome");
        if (out.error != null) throw out.error;                  // thrown AFTER the commit, so the log rows persist
        return out.value;
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
