package za.co.handyflow.platform.secureaccess.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.*;
import za.co.handyflow.platform.secureaccess.domain.model.*;
import za.co.handyflow.platform.secureaccess.domain.repository.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;
import za.co.handyflow.platform.shared.secureaccess.RecipientAccessRules;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Secure, per-recipient access to the documents of a share. Platform-owned; no module's business rules live here.
 *
 * MODULE SIDE (called in-process by the owning module, which has already done its own authorisation):
 * openShare, addRecipient, reissue, revoke, recipients, accessLog.
 *
 * RECIPIENT SIDE (public path; the link token is the credential, never enough on its own):
 *   1. requestOtp  — a six-digit code is emailed to the address on file
 *   2. verifyOtp   — a correct code starts a short session (a second secret, sent as X-Recipient-Session)
 *   3. view / download / acknowledge — each re-checks the link (not expired, not revoked), the session,
 *      the recipient's permission, that the item is assigned to THIS recipient, and then asks the OWNING
 *      MODULE (SecureShareProvider.authorise) whether the document may still be released. Fails closed.
 *      The released bytes are also re-hashed against the hash frozen at issue.
 *
 * Because the code goes to the address on file, a forwarded link is useless to anyone who cannot read that
 * mailbox. Revoking, expiring or re-sending a link ends every open session at once.
 *
 * Every step is written to the append-only access log, including refusals. The public methods are NOT
 * @Transactional: they run their database work through a TransactionTemplate and decide the outcome AFTER
 * the commit, so a wrong code or a refusal is still recorded.
 *
 * Unknown tokens, and tokens that do not look like tokens, all return the same 404. An expired or revoked
 * link returns 410 so a genuine recipient is told to ask for a new one.
 */
@Slf4j
@Service
public class SecureShareService {

    private static final int IP_MAX_REQUESTS  = 60;
    private static final long IP_WINDOW_MS    = 60_000L;
    private static final int OTP_MAX_PER_HOUR = 10;
    private static final long OTP_WINDOW_MS   = 3_600_000L;
    private static final int MAX_ITEMS        = 100;
    private static final int MAX_RECIPIENTS   = 21;

    private final SecureShareRepository          shareRepo;
    private final SecureShareItemRepository      itemRepo;
    private final SecureRecipientRepository      recipientRepo;
    private final SecureRecipientItemRepository  scopeRepo;
    private final SecureAccessLogRepository      logRepo;
    private final SecureCollaboratorRepository   collaboratorRepo;
    private final SecureAccessMailer             mailer;
    private final RateLimiter                    rateLimiter;
    private final Map<String, SecureShareProvider> providers;
    private final TransactionTemplate            tx;
    /** For work done in afterCommit: it must NOT join the transaction that has just committed. */
    private final TransactionTemplate            newTx;

    /** Off by default: links are the standard. When on, small files may also be attached to the email. */
    @Value("${app.secure-access.attachments.enabled:false}")
    private boolean attachmentsEnabled;
    @Value("${app.secure-access.attachments.max-file-bytes:2097152}")
    private long attachMaxFileBytes;
    @Value("${app.secure-access.attachments.max-total-bytes:5242880}")
    private long attachMaxTotalBytes;
    /** Hard ceiling on any file the engine will release, whatever the module allows (10 MB by default). */
    @Value("${app.secure-access.max-release-bytes:10485760}")
    private long maxReleaseBytes;

    public SecureShareService(SecureShareRepository shareRepo, SecureShareItemRepository itemRepo,
                              SecureRecipientRepository recipientRepo, SecureRecipientItemRepository scopeRepo,
                              SecureAccessLogRepository logRepo, SecureCollaboratorRepository collaboratorRepo,
                              SecureAccessMailer mailer, RateLimiter rateLimiter,
                              List<SecureShareProvider> providerBeans, PlatformTransactionManager txManager) {
        this.shareRepo        = shareRepo;
        this.itemRepo         = itemRepo;
        this.recipientRepo    = recipientRepo;
        this.scopeRepo        = scopeRepo;
        this.logRepo          = logRepo;
        this.collaboratorRepo = collaboratorRepo;
        this.mailer           = mailer;
        this.rateLimiter      = rateLimiter;
        this.providers        = providerBeans.stream().collect(Collectors.toMap(
                p -> p.moduleCode().toUpperCase(Locale.ROOT), p -> p, (a, b) -> {
                    throw new IllegalStateException("Two SecureShareProviders for module " + a.moduleCode());
                }));
        this.tx               = new TransactionTemplate(txManager);
        this.newTx            = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ══ Module side ═══════════════════════════════════════════════════════════

    /** Opens a share and gives each recipient their own link. Joins the caller's transaction. */
    @Transactional
    public ShareOpened openShare(OpenShare req) {
        SecureShareProvider provider = provider(req.moduleCode());
        if (req.items() == null || req.items().isEmpty()) throw bad("Select at least one document", "NO_ITEMS");
        if (req.items().size() > MAX_ITEMS) throw bad("At most " + MAX_ITEMS + " documents per share", "TOO_MANY_ITEMS");
        List<RecipientSpec> specs = req.recipients() == null ? List.of() : req.recipients();
        if (specs.size() > MAX_RECIPIENTS) throw bad("Too many recipients", "TOO_MANY_RECIPIENTS");

        Set<String> docKeys = new HashSet<>();
        for (ItemSpec i : req.items()) {
            provider(i.docModuleCode());                                       // every item's module must be registered
            if (!docKeys.add(i.docModuleCode() + "/" + i.docSourceType() + "/" + i.docSourceId())) {
                throw bad("The same document is listed twice", "DUPLICATE_ITEM");
            }
        }
        Set<String> emails = new HashSet<>();
        for (RecipientSpec s : specs) {
            if (!emails.add(RecipientAccessRules.normaliseEmail(s.email()))) {
                throw new HandyFlowException("The same email address is listed twice: " + s.email(),
                        HttpStatus.BAD_REQUEST, "DUPLICATE_RECIPIENT");
            }
        }

        SecureShare share = shareRepo.save(SecureShare.create(req.tenantId(), req.moduleCode().toUpperCase(Locale.ROOT),
                req.sourceType(), req.sourceId(), req.contextId(), req.by(), req.byName()));
        List<SecureShareItem> items = new ArrayList<>();
        int order = 0;
        for (ItemSpec i : req.items()) {
            items.add(SecureShareItem.of(req.tenantId(), share.getId(), i.docModuleCode().toUpperCase(Locale.ROOT),
                    i.docSourceType(), i.docSourceId(), i.revision(), i.displayNumber(), i.title(), i.statusAtIssue(),
                    i.fileName(), i.fileSha256(), order++));
        }
        items = itemRepo.saveAll(items);

        Map<UUID, ReleasedFile> files = req.attachFiles() ? loadAttachments(share, items, provider) : Map.of();
        List<RecipientView> out = new ArrayList<>();
        for (RecipientSpec s : specs) {
            out.add(createRecipient(share, items, s, req.expiryDays(), files, req.by(), req.byName()));
        }
        return new ShareOpened(share.getId(), out);
    }

    /** Adds one more recipient to an existing share. */
    @Transactional
    public RecipientView addRecipient(UUID tenantId, UUID shareId, RecipientSpec spec, Integer expiryDays,
                                      boolean attach, UUID by, String byName) {
        SecureShare share = share(tenantId, shareId);
        String email = RecipientAccessRules.normaliseEmail(spec.email());
        for (SecureRecipient existing : recipientRepo.findByShare(tenantId, shareId)) {
            if (!existing.isRevoked() && existing.getEmail().equals(email)) {
                throw new HandyFlowException("This address already has access", HttpStatus.CONFLICT, "DUPLICATE_RECIPIENT");
            }
        }
        List<SecureShareItem> items = itemRepo.findByShare(tenantId, shareId);
        Map<UUID, ReleasedFile> files = attach ? loadAttachments(share, items, provider(share.getModuleCode())) : Map.of();
        return createRecipient(share, items, spec, expiryDays, files, by, byName);
    }

    /** A new link for the recipient. The old link, any unused code and any open session stop working. */
    @Transactional
    public RecipientView reissue(UUID tenantId, UUID shareId, UUID recipientId, Integer expiryDays, String byName) {
        SecureShare share = share(tenantId, shareId);
        SecureRecipient r = recipientRepo.findByShareAndIdForUpdate(tenantId, shareId, recipientId)
                .orElseThrow(() -> notFound("Recipient"));
        if (r.isRevoked()) {
            throw new HandyFlowException("Access for this recipient was revoked. Add them again to give new access.",
                    HttpStatus.CONFLICT, "RECIPIENT_REVOKED");
        }
        String token = RecipientAccessRules.newSecret();
        r.rotateToken(RecipientAccessRules.hashSecret(token), expiry(expiryDays));
        recipientRepo.save(r);
        log(r, null, null, "LINK_REISSUED", "New link issued; the previous link no longer works", null, byName);
        queueEmail(share, r, token, visibleItems(r), List.of());
        return view(r);
    }

    /** Immediate: the link stops working at once and any open session ends. The module decides WHO may call this. */
    @Transactional
    public RecipientView revoke(UUID tenantId, UUID shareId, UUID recipientId, String reason, String byName) {
        share(tenantId, shareId);
        SecureRecipient r = recipientRepo.findByShareAndIdForUpdate(tenantId, shareId, recipientId)
                .orElseThrow(() -> notFound("Recipient"));
        if (!r.isRevoked()) {
            r.revoke(byName, reason.trim(), Instant.now());
            recipientRepo.save(r);
            log(r, null, null, "REVOKED", reason.trim(), null, byName);
        }
        return view(r);
    }

    @Transactional(readOnly = true)
    public List<RecipientView> recipients(UUID tenantId, UUID shareId) {
        share(tenantId, shareId);
        return recipientViews(tenantId, shareId);
    }

    @Transactional(readOnly = true)
    public List<RecipientView> recipientViews(UUID tenantId, UUID shareId) {
        Instant now = Instant.now();
        return recipientRepo.findByShare(tenantId, shareId).stream()
                .map(r -> RecipientView.of(r, scopeRepo.findItemIds(r.getId()), now)).toList();
    }

    @Transactional(readOnly = true)
    public List<AccessLogView> accessLog(UUID tenantId, UUID shareId) {
        share(tenantId, shareId);
        return logRepo.findByShare(tenantId, shareId).stream().map(AccessLogView::of).toList();
    }

    /** The share a module record opened, if it has one. */
    @Transactional(readOnly = true)
    public Optional<UUID> findShareId(UUID tenantId, String moduleCode, String sourceType, UUID sourceId) {
        return shareRepo.findBySource(tenantId, moduleCode.toUpperCase(Locale.ROOT), sourceType, sourceId).map(SecureShare::getId);
    }

    /** The share-item ids behind a module's own document ids, for module code that needs to map scope. */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> itemIdsByDocument(UUID tenantId, UUID shareId) {
        Map<UUID, UUID> m = new LinkedHashMap<>();
        for (SecureShareItem i : itemRepo.findByShare(tenantId, shareId)) m.put(i.getDocSourceId(), i.getId());
        return m;
    }

    // ══ Recipient side (public) ═══════════════════════════════════════════════

    /** Step 1: email a six-digit code to the address on file. The reply never reveals more than a masked address. */
    public OtpRequested requestOtp(String token, AccessCtx ctx) {
        throttleIp(ctx);
        if (!RecipientAccessRules.secretFormatOk(token)) throw notFoundPublic();
        String hash = RecipientAccessRules.hashSecret(token);
        if (!rateLimiter.tryConsume("sda:otp:" + hash.substring(0, 24), OTP_MAX_PER_HOUR, OTP_WINDOW_MS)) {
            throw tooMany("Too many codes requested. Please try again later.");
        }
        Object[] res = this.<Object[]>inTx(() -> {
            AccessOutcome<SecureRecipient> a = authoriseLink(hash, ctx, true);
            if (a.error != null) return AccessOutcome.fail(a.error);
            SecureRecipient r = a.value;
            Instant now = Instant.now();
            if (!RecipientAccessRules.mayResendOtp(r.getOtpLastSentAt(), now)) {
                return AccessOutcome.fail(tooMany("A code was sent a moment ago. Please wait a minute before asking for another."));
            }
            String code = RecipientAccessRules.newOtp();
            r.issueOtp(RecipientAccessRules.hashOtp(r.getId(), code), now,
                    now.plus(Duration.ofMinutes(RecipientAccessRules.OTP_VALID_MINUTES)));
            recipientRepo.save(r);
            log(r, null, null, "OTP_REQUESTED", "Code emailed to " + RecipientAccessRules.maskEmail(r.getEmail()), ctx, r.getName());
            return AccessOutcome.ok(new Object[]{r, code, referenceOf(r)});
        });
        SecureRecipient r = (SecureRecipient) res[0];
        mailer.sendOtp(r, (String) res[1], (String) res[2]);                       // after the commit
        return new OtpRequested(RecipientAccessRules.maskEmail(r.getEmail()), RecipientAccessRules.OTP_VALID_MINUTES);
    }

    /** Step 2: a correct code starts a session. Wrong guesses are counted and the code locks after five. */
    public SessionGranted verifyOtp(String token, String code, AccessCtx ctx) {
        throttleIp(ctx);
        if (!RecipientAccessRules.secretFormatOk(token)) throw notFoundPublic();
        if (!RecipientAccessRules.otpFormatOk(code)) {
            throw new HandyFlowException("The code is six digits", HttpStatus.BAD_REQUEST, "INVALID_CODE");
        }
        String hash = RecipientAccessRules.hashSecret(token);
        return this.<SessionGranted>inTx(() -> {
            AccessOutcome<SecureRecipient> a = authoriseLink(hash, ctx, true);
            if (a.error != null) return AccessOutcome.fail(a.error);
            SecureRecipient r = a.value;
            Instant now = Instant.now();
            if (r.getOtpHash() == null || r.getOtpExpiresAt() == null || !r.getOtpExpiresAt().isAfter(now)) {
                log(r, null, null, "OTP_FAILED", "No valid code (expired or never requested)", ctx, r.getName());
                return AccessOutcome.fail(new HandyFlowException("That code has expired. Request a new one.",
                        HttpStatus.UNPROCESSABLE_ENTITY, "CODE_EXPIRED"));
            }
            if (RecipientAccessRules.otpLocked(r.getOtpAttempts())) {
                log(r, null, null, "OTP_LOCKED", "Too many wrong codes", ctx, r.getName());
                return AccessOutcome.fail(tooMany("Too many wrong codes. Request a new code."));
            }
            if (!RecipientAccessRules.hashesEqual(RecipientAccessRules.hashOtp(r.getId(), code), r.getOtpHash())) {
                r.registerOtpFailure();
                recipientRepo.save(r);
                log(r, null, null, "OTP_FAILED", "Wrong code (attempt " + r.getOtpAttempts() + ")", ctx, r.getName());
                return AccessOutcome.fail(new HandyFlowException("That code is not correct", HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CODE"));
            }
            String session = RecipientAccessRules.newSecret();
            Instant until = now.plus(Duration.ofMinutes(RecipientAccessRules.SESSION_MINUTES));
            r.startSession(RecipientAccessRules.hashSecret(session), until, now);
            recipientRepo.save(r);
            log(r, null, null, "OTP_VERIFIED", "Email address confirmed", ctx, r.getName());
            return AccessOutcome.ok(new SessionGranted(session, until));
        });
    }

    /** What this recipient may see: only the documents assigned to them. */
    public View view(String token, String session, AccessCtx ctx) {
        throttleIp(ctx);
        return this.<View>inTx(() -> {
            AccessOutcome<SecureRecipient> a = authorise(token, session, ctx, false);
            if (a.error != null) return AccessOutcome.fail(a.error);
            return viewOf(a.value, ctx);
        });
    }

    /**
     * The view for a recipient who has ALREADY been authorised (by their own link and session, or through a
     * collaborator workspace). Must run inside a transaction. Reused by SecureCollaboratorService.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccessOutcome<View> viewOf(SecureRecipient r, AccessCtx ctx) {
        r.touch(Instant.now());
        recipientRepo.save(r);
        SecureShare share = shareRepo.findByTenantAndId(r.getTenantId(), r.getShareId()).orElse(null);
        if (share == null) return AccessOutcome.fail(notFoundPublic());
        ShareRef ref = refOf(share);
        ShareHeader h = describe(share, ref);
        if (h == null) return AccessOutcome.fail(notFoundPublic());
        Who who = whoOf(r);
        List<Document> docs = new ArrayList<>();
        for (SecureShareItem i : visibleItems(r)) {
            Decision d = decide(share, ref, i, who);
            boolean downloadable = r.isCanDownload() && i.getFileSha256() != null && d.allowed();
            docs.add(new Document(i.getDocSourceId(), i.getDisplayNumber(), i.getTitle(), i.getRevision(),
                    i.getStatusAtIssue(), i.getFileName(), i.getFileSha256(), downloadable,
                    d.allowed() ? null : (d.reason() == null ? "No longer available" : d.reason())));
        }
        log(r, null, null, "VIEWED", docs.size() + " document(s) listed", ctx, r.getName());
        return AccessOutcome.ok(new View(h.reference(), h.subject(), h.contextName(), h.purposeLabel(), h.notes(),
                h.sentBy(), h.sentAt(), r.getName(), r.getExpiresAt(), r.isCanDownload(), r.isCanAcknowledge(),
                r.getAcknowledgedAt(), docs));
    }

    /** The exact revision that was issued, only if it is assigned to this recipient, they may download, and the module agrees. */
    public ReleasedFile download(String token, String session, UUID documentId, AccessCtx ctx) {
        throttleIp(ctx);
        return this.<ReleasedFile>inTx(() -> {
            AccessOutcome<SecureRecipient> a = authorise(token, session, ctx, false);
            if (a.error != null) return AccessOutcome.fail(a.error);
            return downloadOf(a.value, documentId, ctx);
        });
    }

    /** As {@link #viewOf}: for a recipient who has already been authorised. Must run inside a transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccessOutcome<ReleasedFile> downloadOf(SecureRecipient r, UUID documentId, AccessCtx ctx) {
        if (!r.isCanDownload()) {
            log(r, documentId, null, "ACCESS_REFUSED", "Download not permitted for this recipient", ctx, r.getName());
            return AccessOutcome.fail(new HandyFlowException("You can view this share but not download its files",
                    HttpStatus.FORBIDDEN, "DOWNLOAD_NOT_PERMITTED"));
        }
        SecureShareItem item = visibleItems(r).stream()
                .filter(i -> i.getDocSourceId().equals(documentId)).findFirst().orElse(null);
        if (item == null) {                                   // not in the share, or not assigned to this recipient
            log(r, documentId, null, "ACCESS_REFUSED", "Document not assigned to this recipient", ctx, r.getName());
            return AccessOutcome.fail(notFoundPublic());
        }
        SecureShare share = shareRepo.findByTenantAndId(r.getTenantId(), r.getShareId()).orElse(null);
        if (share == null) return AccessOutcome.fail(notFoundPublic());
        ShareRef ref = refOf(share);
        Decision d = decide(share, ref, item, whoOf(r));
        if (!d.allowed()) {
            log(r, documentId, item.getRevision(), "ACCESS_REFUSED", "Owning module refused: " + d.reason(), ctx, r.getName());
            return AccessOutcome.fail(new HandyFlowException(d.reason() == null || d.reason().isBlank()
                    ? "This document is no longer available" : d.reason(), HttpStatus.GONE, "DOCUMENT_UNAVAILABLE"));
        }
        try {
            ReleasedFile file = provider(item.getDocModuleCode()).release(r.getTenantId(), ref, docRef(item),
                    item.getFileSha256(), r.getName() + " <" + r.getEmail() + ">");
            if (file == null || file.content() == null) throw unavailable();
            if (file.content().length > maxReleaseBytes) {
                throw new HandyFlowException("This file is too large to release through a secure link",
                        HttpStatus.CONFLICT, "FILE_TOO_LARGE");
            }
            // Defence in depth: the engine re-checks the bytes against the hash frozen at issue, whatever the module did.
            if (item.getFileSha256() != null
                    && !item.getFileSha256().equalsIgnoreCase(RecipientAccessRules.sha256Hex(file.content()))) {
                log.error("Integrity failure share={} item={}: released bytes differ from the hash frozen at issue", share.getId(), item.getId());
                throw new HandyFlowException("This file no longer matches the revision that was issued, so it was not released",
                        HttpStatus.CONFLICT, "FILE_INTEGRITY");
            }
            r.touch(Instant.now());
            recipientRepo.save(r);
            log(r, documentId, item.getRevision(), "DOWNLOADED", item.getFileName(), ctx, r.getName());
            return AccessOutcome.ok(file);
        } catch (HandyFlowException e) {
            log(r, documentId, item.getRevision(), "DOWNLOAD_FAILED", e.getMessage(), ctx, r.getName());
            return AccessOutcome.fail(e);
        } catch (RuntimeException e) {                          // fail closed on any provider fault
            log.error("Provider failed to release share={} item={}", share.getId(), item.getId(), e);
            log(r, documentId, item.getRevision(), "DOWNLOAD_FAILED", "Could not be released", ctx, r.getName());
            return AccessOutcome.fail(unavailable());
        }
    }

    /** Recorded once per recipient. When every active recipient has acknowledged, the owning module is told. */
    public View acknowledge(String token, String session, String note, AccessCtx ctx) {
        throttleIp(ctx);
        this.<Boolean>inTx(() -> {
            AccessOutcome<SecureRecipient> a = authorise(token, session, ctx, true);
            if (a.error != null) return AccessOutcome.fail(a.error);
            return acknowledgeOf(a.value, note, ctx);
        });
        return view(token, session, ctx);
    }

    /** As {@link #viewOf}: for a recipient who has already been authorised. The caller must hold the recipient row lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccessOutcome<Boolean> acknowledgeOf(SecureRecipient r, String note, AccessCtx ctx) {
        if (!r.isCanAcknowledge()) {
            log(r, null, null, "ACCESS_REFUSED", "Acknowledgement not permitted for this recipient", ctx, r.getName());
            return AccessOutcome.fail(new HandyFlowException("You are not able to acknowledge this share",
                    HttpStatus.FORBIDDEN, "ACK_NOT_PERMITTED"));
        }
        if (r.getAcknowledgedAt() != null) {
            return AccessOutcome.fail(new HandyFlowException("You have already acknowledged this share",
                    HttpStatus.CONFLICT, "ALREADY_ACKNOWLEDGED"));
        }
        r.acknowledge(note == null || note.isBlank() ? null : note.trim(), Instant.now());
        recipientRepo.save(r);
        log(r, null, null, "ACKNOWLEDGED", note, ctx, r.getName());

        List<SecureRecipient> active = recipientRepo.findByShare(r.getTenantId(), r.getShareId())
                .stream().filter(x -> !x.isRevoked()).toList();
        boolean all = active.stream().allMatch(x -> x.getAcknowledgedAt() != null);
        SecureShare share = shareRepo.findByTenantAndId(r.getTenantId(), r.getShareId()).orElse(null);
        if (share != null) {
            try {
                provider(share.getModuleCode()).onEvent(r.getTenantId(), refOf(share),
                        new ShareEvent(ShareEvent.ACKNOWLEDGED, r.getId(), r.getName(), all, active.size()));
            } catch (RuntimeException e) {                       // the module's bookkeeping must not undo the acknowledgement
                log.error("Provider event failed share={}", share.getId(), e);
            }
        }
        return AccessOutcome.ok(Boolean.TRUE);
    }

    // ══ Internals ═════════════════════════════════════════════════════════════

    private RecipientView createRecipient(SecureShare share, List<SecureShareItem> items, RecipientSpec s,
                                          Integer expiryDays, Map<UUID, ReleasedFile> files, UUID by, String byName) {
        List<SecureShareItem> scoped = new ArrayList<>();
        if (s.itemIndexes() != null && !s.itemIndexes().isEmpty()) {
            for (Integer idx : new LinkedHashSet<>(s.itemIndexes())) {
                if (idx == null || idx < 0 || idx >= items.size()) {
                    throw new HandyFlowException("A document assigned to " + s.name() + " is not in this share",
                            HttpStatus.BAD_REQUEST, "DOCUMENT_NOT_IN_SHARE");
                }
                scoped.add(items.get(idx));
            }
        }
        boolean all = scoped.isEmpty() || scoped.size() == items.size();

        String token = RecipientAccessRules.newSecret();
        SecureRecipient r = recipientRepo.save(SecureRecipient.create(
                share.getTenantId(), share.getId(), s.name().trim(), blankToNull(s.organisation()),
                RecipientAccessRules.normaliseEmail(s.email()), RecipientAccessRules.hashSecret(token),
                s.canDownload() == null || s.canDownload(), s.canAcknowledge() == null || s.canAcknowledge(),
                expiry(expiryDays), by, byName));
        if (!all) {
            scopeRepo.saveAll(scoped.stream().map(i -> SecureRecipientItem.of(r.getId(), i.getId())).toList());
        }
        // A registered collaborator account with this address sees the share in their workspace as well.
        collaboratorRepo.findActiveByEmail(share.getTenantId(), r.getEmail()).ifPresent(c -> {
            r.linkCollaborator(c.getId());
            recipientRepo.save(r);
        });
        log(r, null, null, "LINK_ISSUED", "Link valid until " + r.getExpiresAt()
                + (all ? ", all documents" : ", " + scoped.size() + " document(s)")
                + (r.getCollaboratorId() != null ? ", registered collaborator" : ""), null, byName);
        List<SecureShareItem> visible = all ? items : scoped;
        queueEmail(share, r, token, visible, attachmentsFor(r, visible, files));
        return RecipientView.of(r, all ? List.of() : scoped.stream().map(SecureShareItem::getId).toList(), Instant.now());
    }

    /** Sends only after the surrounding transaction commits, so a rolled-back share never emails a dead link. */
    private void queueEmail(SecureShare share, SecureRecipient r, String token, List<SecureShareItem> items,
                            List<EmailService.Attachment> attachments) {
        String attached = attachments.isEmpty() ? "" : "; " + attachments.size() + " file(s) attached: "
                + attachments.stream().map(EmailService.Attachment::name).collect(Collectors.joining(", "));
        ShareRef ref = refOf(share);
        UUID tenantId = share.getTenantId();
        Runnable send = () -> {
            try {
                ShareHeader h = newTx.execute(s -> describe(share, ref));      // the owning module's record is committed by now
                if (h == null) {
                    log.error("Share email not sent: the owning module has no record for share={}", share.getId());
                    return;
                }
                boolean queued = mailer.sendShare(h, r, token, items, attachments);
                newTx.executeWithoutResult(s -> recipientRepo.findById(r.getId()).ifPresent(x -> {
                    if (queued) { x.markEmailSent(Instant.now()); recipientRepo.save(x); }
                    log(x, null, null, queued ? "EMAIL_QUEUED" : "EMAIL_NOT_SENT",
                            queued ? "Link emailed to " + RecipientAccessRules.maskEmail(x.getEmail()) + attached
                                   : "Notifications are switched off; no email was sent", null, "System");
                }));
            } catch (RuntimeException e) {
                log.error("Could not queue share email tenant={} recipient={}", tenantId, r.getId(), e);
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

    /**
     * Reads the issued revision of every stored file ONCE, checking the switch and the size caps. Nothing is
     * attached unless an administrator turned app.secure-access.attachments.enabled on AND the sender asked for it.
     */
    private Map<UUID, ReleasedFile> loadAttachments(SecureShare share, List<SecureShareItem> items, SecureShareProvider ignored) {
        if (!attachmentsEnabled) {
            throw new HandyFlowException("Attaching files to the email is switched off. Recipients receive a secure link instead.",
                    HttpStatus.BAD_REQUEST, "ATTACHMENTS_DISABLED");
        }
        ShareRef ref = refOf(share);
        Map<UUID, ReleasedFile> files = new LinkedHashMap<>();
        for (SecureShareItem i : items) {
            if (i.getFileSha256() == null) {
                throw new HandyFlowException(label(i) + " is a link, not a stored file, so it cannot be attached",
                        HttpStatus.BAD_REQUEST, "NOT_ATTACHABLE");
            }
            ReleasedFile f = provider(i.getDocModuleCode()).release(share.getTenantId(), ref, docRef(i), i.getFileSha256(), "Emailed as attachment");
            if (f == null || f.content() == null) throw unavailable();
            if (f.content().length > attachMaxFileBytes) {
                throw new HandyFlowException(label(i) + " is " + (f.content().length / 1024) + " KB, over the "
                        + (attachMaxFileBytes / 1024) + " KB limit for attachments. Send a secure link instead.",
                        HttpStatus.BAD_REQUEST, "ATTACHMENT_TOO_LARGE");
            }
            if (!i.getFileSha256().equalsIgnoreCase(RecipientAccessRules.sha256Hex(f.content()))) {
                throw new HandyFlowException(label(i) + " no longer matches the revision being issued",
                        HttpStatus.CONFLICT, "FILE_INTEGRITY");
            }
            files.put(i.getId(), f);
        }
        return files;
    }

    /** The files this recipient may receive: only their assigned items, and only if they may download. */
    private List<EmailService.Attachment> attachmentsFor(SecureRecipient r, List<SecureShareItem> visible, Map<UUID, ReleasedFile> files) {
        if (files.isEmpty() || !r.isCanDownload()) return List.of();
        List<EmailService.Attachment> out = new ArrayList<>();
        long total = 0;
        for (SecureShareItem i : visible) {
            ReleasedFile f = files.get(i.getId());
            if (f == null) continue;
            total += f.content().length;
            String prefix = (i.getDisplayNumber() != null ? i.getDisplayNumber() : "doc")
                    + (i.getRevision() != null ? " rev " + i.getRevision() : "") + " - ";
            out.add(new EmailService.Attachment(safeName(prefix + f.fileName()), f.content(), f.contentType()));
        }
        if (total > attachMaxTotalBytes) {
            throw new HandyFlowException("The files for " + r.getName() + " total " + (total / 1024) + " KB, over the "
                    + (attachMaxTotalBytes / 1024) + " KB limit for one email. Send a secure link instead.",
                    HttpStatus.BAD_REQUEST, "ATTACHMENT_TOO_LARGE");
        }
        return out;
    }

    private static String safeName(String name) {
        String n = name == null ? "document" : name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        while (n.startsWith(".")) n = n.substring(1);
        if (n.length() > 150) n = n.substring(0, 150);
        return n.isEmpty() ? "document" : n;
    }

    private static String label(SecureShareItem i) {
        return (i.getDisplayNumber() != null ? i.getDisplayNumber() : i.getTitle())
                + (i.getRevision() != null ? " rev " + i.getRevision() : "");
    }

    private List<SecureShareItem> visibleItems(SecureRecipient r) {
        List<SecureShareItem> all = itemRepo.findByShare(r.getTenantId(), r.getShareId());
        Set<UUID> scope = new HashSet<>(scopeRepo.findItemIds(r.getId()));
        return scope.isEmpty() ? all : all.stream().filter(i -> scope.contains(i.getId())).toList();
    }

    private String referenceOf(SecureRecipient r) {
        SecureShare share = shareRepo.findByTenantAndId(r.getTenantId(), r.getShareId()).orElse(null);
        if (share == null) return "your documents";
        try {
            ShareHeader h = describe(share, refOf(share));
            return h == null || h.reference() == null ? "your documents" : h.reference();
        } catch (RuntimeException e) {
            return "your documents";
        }
    }

    // — provider access (fail closed) —

    private SecureShareProvider provider(String moduleCode) {
        SecureShareProvider p = moduleCode == null ? null : providers.get(moduleCode.toUpperCase(Locale.ROOT));
        if (p == null) throw bad("Unknown module for secure sharing: " + moduleCode, "UNKNOWN_MODULE");
        return p;
    }

    private ShareHeader describe(SecureShare share, ShareRef ref) {
        return provider(share.getModuleCode()).describe(share.getTenantId(), ref);
    }

    private Decision decide(SecureShare share, ShareRef ref, SecureShareItem item, Who who) {
        try {
            Decision d = provider(item.getDocModuleCode()).authorise(share.getTenantId(), ref, docRef(item), who);
            return d == null ? Decision.deny("No longer available") : d;
        } catch (RuntimeException e) {
            log.error("Provider authorise failed share={} item={}", share.getId(), item.getId(), e);
            return Decision.deny("No longer available");
        }
    }

    private static ShareRef refOf(SecureShare s) {
        return new ShareRef(s.getId(), s.getModuleCode(), s.getSourceType(), s.getSourceId(), s.getContextId());
    }

    private static DocumentRef docRef(SecureShareItem i) {
        return new DocumentRef(i.getDocModuleCode(), i.getDocSourceType(), i.getDocSourceId(), i.getRevision());
    }

    private static Who whoOf(SecureRecipient r) {
        return new Who(r.getId(), r.getCollaboratorId(), r.getName(), r.getEmail());
    }

    /** Link checks only: format, known, not expired, not revoked. Used before a session exists. */
    private AccessOutcome<SecureRecipient> authoriseLink(String hash, AccessCtx ctx, boolean lock) {
        Optional<SecureRecipient> o = lock ? recipientRepo.findByTokenHashForUpdate(hash) : recipientRepo.findByTokenHash(hash);
        if (o.isEmpty()) return AccessOutcome.fail(notFoundPublic());
        SecureRecipient r = o.get();
        if (!RecipientAccessRules.linkUsable(r.getExpiresAt(), r.getRevokedAt(), Instant.now())) {
            log(r, null, null, "ACCESS_REFUSED", r.isRevoked() ? "Link revoked" : "Link expired", ctx, r.getName());
            return AccessOutcome.fail(new HandyFlowException("This link has expired or was withdrawn. Ask the sender to issue a new one.",
                    HttpStatus.GONE, "LINK_UNAVAILABLE"));
        }
        return AccessOutcome.ok(r);
    }

    /** Link AND session. */
    private AccessOutcome<SecureRecipient> authorise(String token, String session, AccessCtx ctx, boolean lock) {
        if (!RecipientAccessRules.secretFormatOk(token)) return AccessOutcome.fail(notFoundPublic());
        AccessOutcome<SecureRecipient> a = authoriseLink(RecipientAccessRules.hashSecret(token), ctx, lock);
        if (a.error != null) return a;
        SecureRecipient r = a.value;
        boolean ok = session != null && RecipientAccessRules.secretFormatOk(session)
                && RecipientAccessRules.sessionUsable(r.getSessionExpiresAt(), Instant.now())
                && RecipientAccessRules.hashesEqual(RecipientAccessRules.hashSecret(session), r.getSessionHash());
        if (!ok) {
            if (session != null) log(r, null, null, "ACCESS_REFUSED", "Session invalid or expired", ctx, r.getName());
            return AccessOutcome.fail(new HandyFlowException("Please confirm your email address to continue",
                    HttpStatus.UNAUTHORIZED, "VERIFICATION_REQUIRED"));
        }
        return AccessOutcome.ok(r);
    }

    private void log(SecureRecipient r, UUID docSourceId, String revision, String action, String detail,
                     AccessCtx ctx, String actor) {
        logRepo.save(SecureAccessLog.of(r.getTenantId(), r.getShareId(), r.getId(), docSourceId, revision, action, detail,
                ctx == null ? null : ctx.ip(), ctx == null ? null : ctx.userAgent(), actor));
    }

    private void throttleIp(AccessCtx ctx) {
        String ip = (ctx == null || ctx.ip() == null || ctx.ip().isBlank()) ? "unknown" : ctx.ip();
        if (!rateLimiter.tryConsume("sda:ip:" + ip, IP_MAX_REQUESTS, IP_WINDOW_MS)) {
            throw tooMany("Too many requests — please try again shortly");
        }
    }

    private static Instant expiry(Integer days) {
        return Instant.now().plus(Duration.ofDays(RecipientAccessRules.clampExpiryDays(days)));
    }

    private RecipientView view(SecureRecipient r) {
        return RecipientView.of(r, scopeRepo.findItemIds(r.getId()), Instant.now());
    }

    private SecureShare share(UUID tenantId, UUID shareId) {
        return shareRepo.findByTenantAndId(tenantId, shareId).orElseThrow(() -> notFound("Share"));
    }

    private <T> T inTx(Supplier<AccessOutcome<T>> body) {
        AccessOutcome<T> out = tx.execute(status -> body.get());
        if (out == null) throw new IllegalStateException("Transaction returned no outcome");
        if (out.error != null) throw out.error;                  // thrown AFTER the commit, so the log rows persist
        return out.value;
    }

    private static HandyFlowException unavailable() {
        return new HandyFlowException("This document is no longer available", HttpStatus.GONE, "DOCUMENT_UNAVAILABLE");
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
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
