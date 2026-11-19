package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.marketing.domain.model.*;
import za.co.handyflow.platform.marketing.domain.repository.*;
import za.co.handyflow.platform.marketing.dto.*;
import za.co.handyflow.platform.shared.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingService {

    private final MktContactPreferenceRepository preferenceRepo;
    private final MktTemplateRepository          templateRepo;
    private final MktCampaignRepository          campaignRepo;
    private final MktCampaignContactRepository   campaignContactRepo;
    private final MktSendQueueRepository         sendQueueRepo;
    private final EmailService                   emailService;
    private final JdbcTemplate                   jdbc;
    private final CrmFacade                      crmFacade;
    private final MarketingConsentService        consentService;
    private final MarketingAudienceService       audienceService;
    private final MarketingSegmentService        segmentService;
    private final MarketingLifecycleService     lifecycle;
    private final MarketingFormService          formService;
    private final TenantFacade                   tenantFacade;

    @Value("${app.frontend.url:https://app.handyflow.co.za}")
    private String frontendUrl;

    @Value("${app.api.public-url:https://app.handyflow.co.za}")
    private String apiPublicUrl;

    private String unsubscribeBaseUrl() { return frontendUrl + "/unsubscribe/"; }
    private String trackOpenBaseUrl()   { return apiPublicUrl + "/api/v1/marketing/track/open/"; }
    private String trackClickBaseUrl()  { return apiPublicUrl + "/api/v1/marketing/track/click/"; }

    private static final int    MAX_IMPORT_ROWS    = 5_000;
    private static final int    IN_CLAUSE_CHUNK    = 1_000;
    private static final String AUDIENCE_ALL       = "ALL_OPTED_IN";
    private static final String AUDIENCE_SEGMENT   = "SEGMENT";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Set<String> COUNTER_COLUMNS = Set.of("open_count", "click_count");

    private static final byte[] TRANSPARENT_PIXEL = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
            (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            0x21, (byte) 0xF9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
    };

    private static final Pattern HREF_PATTERN = Pattern.compile("href=\"(https?://[^\"]+)\"");

    /** Reference to a scheduled campaign that is due, used by the scheduler to launch each in its own transaction. */
    public record DueCampaign(TenantId tenantId, UUID campaignId) {}

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MarketingSummaryResponse getSummary(TenantId tenantId) {
        long optedIn   = preferenceRepo.countOptedIn(tenantId);
        long optedOut  = countOptedOut(tenantId);
        long total     = optedIn + optedOut;
        long drafts    = countCampaignsByStatus(tenantId, "DRAFT");
        long sent      = countCampaignsByStatus(tenantId, "SENT");
        long scheduled = countCampaignsByStatus(tenantId, "SCHEDULED");
        long pending   = countPendingQueueItems(tenantId);
        long suppressions = consentService.countActiveSuppressions(tenantId);
        long exceptions   = audienceService.countConsentExceptions(tenantId.getValue());
        return new MarketingSummaryResponse(total, optedIn, optedOut,
                drafts, sent, scheduled, pending, suppressions, exceptions);
    }

    // ── Contact preferences (POPIA) ───────────────────────────────────────────

    /** Server-side search, filter and pagination. status: ALL | OPTED_IN | NOT_OPTED_IN | SUPPRESSED. */
    @Transactional(readOnly = true)
    public Page<ContactPreferenceResponse> getContacts(TenantId tenantId, String search, String status,
                                                       String source, Pageable pageable) {
        return audienceService.searchContacts(tenantId.getValue(), search, status, source, pageable);
    }

    @Transactional
    public ContactPreferenceResponse optIn(TenantId tenantId, UUID actingUser, String email,
                                           String name, String source, String evidence) {
        String normalised = normaliseEmail(email);
        if (!isValidEmail(normalised)) {
            throw new HandyFlowException("Invalid email address", HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        if (consentService.isSuppressed(tenantId, normalised)) {
            throw new HandyFlowException(
                    "This address is on the suppression list (unsubscribed, complained or bounced) and "
                            + "cannot be opted in. Lift the suppression with a documented reason first.",
                    HttpStatus.CONFLICT, "CONTACT_SUPPRESSED");
        }

        String cleanSource = cleanSource(source);
        MktContactPreference pref = preferenceRepo
                .findByTenantIdAndEmail(tenantId, normalised)
                .orElseGet(() -> MktContactPreference.create(
                        tenantId, "SUBSCRIBER", null, normalised, name, false, cleanSource));

        boolean wasAlreadyOptedIn = pref.isEmailOptedIn();
        if (!wasAlreadyOptedIn) {
            pref.optIn(cleanSource);
        }
        preferenceRepo.save(pref);

        if (!wasAlreadyOptedIn) {
            consentService.recordOptIn(tenantId, pref, cleanSource, evidence, actingUser);
            grantCrmConsent(tenantId, pref, cleanSource);
        }

        log.info("Opted in: email={} tenant={} newOptIn={}", normalised, tenantId, !wasAlreadyOptedIn);
        return toPreferenceResponse(pref);
    }

    @Transactional
    public ImportContactsResult importContacts(TenantId tenantId, UUID actingUser, ImportContactsRequest req) {
        if (req.contacts().size() > MAX_IMPORT_ROWS) {
            throw new HandyFlowException(
                    "Import is limited to " + MAX_IMPORT_ROWS + " contacts at a time",
                    HttpStatus.BAD_REQUEST, "IMPORT_TOO_LARGE");
        }
        String source = cleanSource(req.optInSource() != null ? req.optInSource() : "IMPORT");
        Set<String> suppressed = consentService.activeSuppressedEmails(tenantId);

        Map<String, ImportContactsRequest.ContactEntry> byEmail = new LinkedHashMap<>();
        Map<String, Boolean> wantsOptIn = new HashMap<>();
        int invalid = 0, skippedSuppressed = 0;
        for (ImportContactsRequest.ContactEntry entry : req.contacts()) {
            String email = normaliseEmail(entry.email());
            if (!isValidEmail(email)) { invalid++; continue; }
            if (suppressed.contains(email)) { skippedSuppressed++; continue; }
            byEmail.putIfAbsent(email, entry);
            wantsOptIn.merge(email, entry.emailOptedIn(), Boolean::logicalOr);
        }

        Map<String, MktContactPreference> existing = loadPreferencesByEmail(tenantId, byEmail.keySet());
        List<MktContactPreference> toSave = new ArrayList<>();
        for (Map.Entry<String, ImportContactsRequest.ContactEntry> row : byEmail.entrySet()) {
            String email = row.getKey();
            ImportContactsRequest.ContactEntry entry = row.getValue();
            MktContactPreference pref = existing.get(email);
            if (pref == null) {
                pref = MktContactPreference.create(tenantId, "SUBSCRIBER", null,
                        email, entry.name(), false, source);
            }
            boolean wasAlreadyOptedIn = pref.isEmailOptedIn();
            if (wantsOptIn.getOrDefault(email, false) && !wasAlreadyOptedIn) {
                pref.optIn(source);
                consentService.recordImportDeclaration(tenantId, pref, source, actingUser);
                grantCrmConsent(tenantId, pref, source);
            }
            toSave.add(pref);
        }
        preferenceRepo.saveAll(toSave);

        log.info("Import tenant={} imported={} suppressedSkipped={} invalidSkipped={}",
                tenantId, byEmail.size(), skippedSuppressed, invalid);
        return new ImportContactsResult(byEmail.size(), skippedSuppressed, invalid);
    }

    @Transactional
    public int syncCrmContacts(TenantId tenantId) {
        List<CustomerSummary> customers = crmFacade.findActiveCustomersWithEmail(tenantId);

        List<String> candidateEmails = customers.stream()
                .map(CustomerSummary::email)
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.toLowerCase().trim())
                .distinct()
                .toList();
        Set<String> alreadyExists = candidateEmails.isEmpty()
                ? Set.of()
                : new HashSet<>(preferenceRepo.findExistingEmails(tenantId, candidateEmails));

        int synced = 0;
        for (CustomerSummary customer : customers) {
            String email = customer.email();
            if (email == null || email.isBlank()) continue;
            String normalizedEmail = email.toLowerCase().trim();
            if (!alreadyExists.contains(normalizedEmail)) {
                // Synced contacts are NOT opted in — CRM existence is never consent.
                MktContactPreference pref = MktContactPreference.create(
                        tenantId, "CUSTOMER", customer.id(),
                        email, customer.name(), false, "CRM_SYNC");
                preferenceRepo.save(pref);
                alreadyExists.add(normalizedEmail);
                synced++;
            }
        }
        log.info("Synced {} CRM customers for tenant={}", synced, tenantId);
        return synced;
    }

    @Transactional(readOnly = true)
    public List<ConsentEvidenceResponse> getConsentHistory(TenantId tenantId, UUID preferenceId) {
        MktContactPreference pref = preferenceRepo.findByIdAndTenantId(preferenceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", preferenceId.toString()));
        return consentService.history(tenantId, pref.getEmail()).stream()
                .map(e -> new ConsentEvidenceResponse(e.getId(), e.getEventType(), e.getChannel(),
                        e.getPurpose(), e.getSource(), e.getEvidenceText(),
                        e.getRecordedBy(), e.getCreatedAt()))
                .toList();
    }

    // ── Suppressions ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SuppressionResponse> getSuppressions(TenantId tenantId, boolean activeOnly, Pageable pageable) {
        return consentService.list(tenantId, activeOnly, pageable).map(this::toSuppressionResponse);
    }

    @Transactional
    public SuppressionResponse addSuppression(TenantId tenantId, UUID actingUser, AddSuppressionRequest req) {
        String email = normaliseEmail(req.email());
        if (!isValidEmail(email)) {
            throw new HandyFlowException("Invalid email address", HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        String reason = req.reason() != null ? req.reason() : MktSuppressionEntry.REASON_MANUAL;
        return toSuppressionResponse(
                suppressAndCascade(tenantId, email, reason, "STAFF", req.note(), actingUser));
    }

    /**
     * Called by the delivery engine (synchronous SMTP rejection) and by provider webhooks
     * (hard bounce, complaint, unsubscribe). Idempotent.
     */
    @Transactional
    public void applyDeliverySuppression(TenantId tenantId, String email, String reason,
                                         String source, String note) {
        String e = normaliseEmail(email);
        if (!isValidEmail(e)) {
            log.warn("applyDeliverySuppression ignored invalid address '{}' (tenant={})", email, tenantId);
            return;
        }
        String cleanSource = source == null ? "PROVIDER" : cleanSource(source);
        suppressAndCascade(tenantId, e, reason, cleanSource, note, null);
    }

    private MktSuppressionEntry suppressAndCascade(TenantId tenantId, String email, String reason,
                                                   String source, String note, UUID by) {
        MktSuppressionEntry entry = consentService.suppress(tenantId, email, reason, source, note, by);

        Optional<MktContactPreference> pref = preferenceRepo.findByTenantIdAndEmail(tenantId, email);
        pref.ifPresent(p -> {
            if (p.isEmailOptedIn()) {
                p.optOut();
                preferenceRepo.save(p);
                // A hard bounce means the ADDRESS is bad, not that the person withdrew consent.
                if (!MktSuppressionEntry.REASON_HARD_BOUNCE.equals(reason)) {
                    syncOptOutToCrm(tenantId, p, "Suppressed (" + reason + ")");
                }
            }
        });
        cascadeSuppressionToCampaigns(tenantId, email, pref.map(MktContactPreference::getId).orElse(null),
                "SUPPRESSED: " + reason);
        return entry;
    }

    @Transactional
    public SuppressionResponse liftSuppression(TenantId tenantId, UUID actingUser, UUID suppressionId, String reason) {
        return toSuppressionResponse(consentService.lift(tenantId, suppressionId, reason, actingUser));
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TemplateResponse> getTemplates(TenantId tenantId) {
        return templateRepo.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(tenantId)
                .stream().map(this::toTemplateResponse).toList();
    }

    @Transactional
    public TemplateResponse createTemplate(TenantId tenantId, UUID createdBy,
                                           CreateTemplateRequest req) {
        MktTemplate t = MktTemplate.create(tenantId, req.name(), req.subject(),
                req.htmlBody(), req.previewText(), req.category(), createdBy);
        templateRepo.save(t);
        return toTemplateResponse(t);
    }

    @Transactional
    public TemplateResponse updateTemplate(TenantId tenantId, UUID id,
                                           CreateTemplateRequest req) {
        MktTemplate t = templateRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template", id.toString()));
        t.update(req.name(), req.subject(), req.htmlBody(), req.previewText(), req.category());
        templateRepo.save(t);
        return toTemplateResponse(t);
    }

    // ── Campaigns ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CampaignResponse> getCampaigns(TenantId tenantId, Pageable pageable) {
        return campaignRepo.findAll(tenantId, pageable).map(this::toCampaignResponse);
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaign(TenantId tenantId, UUID id) {
        return toCampaignResponse(findCampaign(tenantId, id));
    }

    @Transactional
    public CampaignResponse createCampaign(TenantId tenantId, UUID createdBy,
                                           CreateCampaignRequest req) {
        String audienceType = req.audienceType() != null ? req.audienceType().toUpperCase() : AUDIENCE_ALL;
        UUID segmentId = null;
        String segmentName = null;
        if (AUDIENCE_SEGMENT.equals(audienceType)) {
            segmentName = segmentService.requireActiveName(tenantId, req.segmentId());   // 400 if missing/deleted
            segmentId = req.segmentId();
        } else if (!AUDIENCE_ALL.equals(audienceType)) {
            throw new HandyFlowException(
                    "Audience type '" + audienceType + "' is not supported. Use ALL_OPTED_IN or SEGMENT.",
                    HttpStatus.BAD_REQUEST, "AUDIENCE_NOT_SUPPORTED");
        }

        String subject  = req.subject();
        String htmlBody = req.htmlBody();
        if (req.templateId() != null) {
            MktTemplate tmpl = templateRepo.findByIdAndTenantId(req.templateId(), tenantId)
                    .orElseThrow(() -> new HandyFlowException(
                            "Template not found", HttpStatus.BAD_REQUEST, "NOT_FOUND"));
            if (subject  == null) subject  = tmpl.getSubject();
            if (htmlBody == null) htmlBody = tmpl.getHtmlBody();
        }
        if (subject == null || htmlBody == null) {
            throw new HandyFlowException(
                    "Campaign requires either a template or explicit subject and htmlBody",
                    HttpStatus.BAD_REQUEST, "MISSING_CONTENT");
        }
        if (req.scheduledAt() != null && req.scheduledAt().isBefore(Instant.now().minusSeconds(60))) {
            throw new HandyFlowException("Scheduled time must be in the future",
                    HttpStatus.BAD_REQUEST, "SCHEDULE_IN_PAST");
        }

        MktCampaign campaign = MktCampaign.create(tenantId, req.name(), req.channel(),
                req.templateId(), subject, htmlBody, audienceType, req.audienceFilter(),
                segmentId, segmentName,
                req.scheduledAt(), req.fromName(), req.replyTo(), createdBy);
        if (req.scheduledAt() != null) {
            campaign.schedule(req.scheduledAt());
        }
        campaignRepo.save(campaign);
        lifecycle.recordVersion(campaign, "Created", createdBy);
        log.info("Created campaign={} name={} status={} audience={}", campaign.getId(), req.name(),
                campaign.getStatus(), audienceType);
        return toCampaignResponse(campaign);
    }

    /**
     * Launch (DRAFT/SCHEDULED) or resume (PAUSED).
     * Launch RESOLVES the audience now (dynamic segment rules are evaluated at this moment), then
     * snapshots it: each member becomes a campaign-contact row, either PENDING (queued) or SUPPRESSED.
     * Eligibility is checked AGAIN immediately before each send by MarketingDeliveryService.
     */
    @Transactional
    public CampaignResponse launchCampaign(TenantId tenantId, UUID id) {
        MktCampaign campaign = findCampaign(tenantId, id);

        if ("PAUSED".equals(campaign.getStatus())) {
            campaign.resume();
            campaignRepo.save(campaign);
            return toCampaignResponse(campaign);
        }
        if (!campaign.isDraft() && !"SCHEDULED".equals(campaign.getStatus())) {
            throw new HandyFlowException(
                    "Only DRAFT, SCHEDULED or PAUSED campaigns can be launched",
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        lifecycle.assertLaunchAllowed(tenantId, campaign);   // lint errors + approval gate
        List<MktContactPreference> optedIn;
        if (AUDIENCE_SEGMENT.equals(campaign.getAudienceType())) {
            String segmentName = segmentService.requireActiveName(tenantId, campaign.getSegmentId());
            campaign.refreshSegmentName(segmentName);
            optedIn = loadOptedInPreferences(tenantId,
                    segmentService.resolveOptedInAudience(tenantId, campaign.getSegmentId()));
        } else if (AUDIENCE_ALL.equals(campaign.getAudienceType())) {
            optedIn = preferenceRepo.findAllOptedIn(tenantId);
        } else {
            throw new HandyFlowException(
                    "Audience type '" + campaign.getAudienceType() + "' is not supported.",
                    HttpStatus.BAD_REQUEST, "AUDIENCE_NOT_SUPPORTED");
        }

        Set<String> suppressed = consentService.activeSuppressedEmails(tenantId);

        List<MktContactPreference> eligible = new ArrayList<>();
        List<MktContactPreference> excluded = new ArrayList<>();
        for (MktContactPreference pref : optedIn) {
            if (consentService.evaluate(pref, pref.getEmail(), suppressed).eligible()) eligible.add(pref);
            else excluded.add(pref);
        }

        if (campaign.getTopicId() != null) {   // contacts who opted out of this topic are not emailed
            Set<UUID> topicOptOut = formService.topicOptedOut(tenantId, campaign.getTopicId());
            eligible.removeIf(p -> topicOptOut.contains(p.getId()));
        }

        if (eligible.isEmpty()) {
            throw new HandyFlowException(
                    "No eligible recipients: nobody in this audience is opted in, or all matching contacts are suppressed.",
                    HttpStatus.BAD_REQUEST, "NO_AUDIENCE");
        }

        Set<UUID> alreadySnapshotted = campaignContactRepo.findByCampaignId(campaign.getId()).stream()
                .map(MktCampaignContact::getPreferenceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        String tenantName = fetchTenantName(tenantId);
        campaign.startSending(eligible.size());
        campaign.recordSentVersion();
        campaign.recordLaunchSuppressed(excluded.size());
        campaignRepo.save(campaign);

        List<MktCampaignContact> contacts = new ArrayList<>();
        List<MktSendQueue> queue = new ArrayList<>();

        for (MktContactPreference pref : eligible) {
            if (alreadySnapshotted.contains(pref.getId())) continue;

            MktCampaignContact cc = MktCampaignContact.create(
                    campaign.getId(), tenantId.getValue(),
                    pref.getEmail(), pref.getName(), pref.getId());
            contacts.add(cc);

            String personalised = personalise(campaign.getHtmlBody(), pref, tenantName, true, cc.getId(), campaign);
            String subject      = personalise(campaign.getSubject(),  pref, tenantName, false, null, campaign);
            queue.add(MktSendQueue.create(
                    campaign.getId(), cc.getId(), tenantId.getValue(),
                    pref.getEmail(), pref.getName(), subject, personalised,
                    campaign.getScheduledAt()));
        }
        for (MktContactPreference pref : excluded) {
            if (alreadySnapshotted.contains(pref.getId())) continue;
            MktCampaignContact cc = MktCampaignContact.create(
                    campaign.getId(), tenantId.getValue(),
                    pref.getEmail(), pref.getName(), pref.getId());
            cc.markSuppressed("SUPPRESSED: " + MarketingConsentService.BLOCKED_SUPPRESSED + " at launch");
            contacts.add(cc);
        }
        campaignContactRepo.saveAll(contacts);
        sendQueueRepo.saveAll(queue);

        log.info("Launched campaign={} audience={} queued={} excluded={}", id, campaign.getAudienceType(),
                queue.size(), excluded.size());
        return toCampaignResponse(campaign);
    }

    @Transactional
    public CampaignResponse pauseCampaign(TenantId tenantId, UUID id) {
        MktCampaign campaign = findCampaign(tenantId, id);
        campaign.pause();
        campaignRepo.save(campaign);
        return toCampaignResponse(campaign);
    }

    @Transactional
    public CampaignResponse cancelCampaign(TenantId tenantId, UUID id) {
        MktCampaign campaign = findCampaign(tenantId, id);
        campaign.cancel();
        campaignRepo.save(campaign);
        jdbc.update("UPDATE mkt_send_queue SET status = 'DEAD', error_message = 'Campaign cancelled', "
                + "processed_at = now() WHERE campaign_id = ? AND status = 'PENDING'", id);
        jdbc.update("UPDATE mkt_campaign_contacts SET status = 'CANCELLED' "
                + "WHERE campaign_id = ? AND status = 'PENDING'", id);
        return toCampaignResponse(campaign);
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DueCampaign> findDueScheduledCampaigns() {
        return campaignRepo.findScheduledReady(Instant.now()).stream()
                .map(c -> new DueCampaign(c.getTenantId(), c.getId()))
                .toList();
    }

    /** One transaction per due campaign, so a failure in one never affects the others. */
    @Transactional
    public void launchDueCampaign(DueCampaign due) {
        try {
            launchCampaign(due.tenantId(), due.campaignId());
        } catch (HandyFlowException e) {
            log.warn("Scheduled campaign={} could not launch ({}): {}",
                    due.campaignId(), e.getErrorCode(), e.getMessage());
            campaignRepo.findByIdAndTenantId(due.campaignId(), due.tenantId()).ifPresent(c -> {
                c.markFailed(e.getMessage());
                campaignRepo.save(c);
            });
        }
    }

    // ── Unsubscribe ───────────────────────────────────────────────────────────

    /** Used by both the GET link click and the RFC 8058 one-click POST. Idempotent. */
    @Transactional
    public void handleUnsubscribe(String token) {
        MktContactPreference pref = preferenceRepo.findByUnsubscribeToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "This unsubscribe link is invalid or has expired.",
                        HttpStatus.NOT_FOUND, "UNSUBSCRIBE_TOKEN_INVALID"));

        TenantId tenantId = pref.getTenantId();
        boolean wasOptedIn = pref.isEmailOptedIn();

        pref.optOut();
        preferenceRepo.save(pref);

        consentService.recordOptOut(tenantId, pref, "EMAIL_UNSUBSCRIBE_LINK");

        cascadeSuppressionToCampaigns(tenantId, pref.getEmail(), pref.getId(),
                "SUPPRESSED: recipient unsubscribed");

        if (wasOptedIn) {
            final String email = pref.getEmail();
            final String name  = pref.getName();
            final String tenantName = fetchTenantName(tenantId);
            afterCommit(() -> {
                try {
                    emailService.send(email, "You've been unsubscribed",
                            buildUnsubscribeConfirmationEmail(tenantName, name));
                } catch (Exception e) {
                    log.error("Failed to send unsubscribe confirmation to={}: {}", email, e.getMessage(), e);
                }
            });
            syncOptOutToCrm(tenantId, pref, "Unsubscribed via email link");
        }
        log.info("Unsubscribed: email={} firstTransition={}", pref.getEmail(), wasOptedIn);
    }

    // ── Open/click tracking (public, no auth) ─────────────────────────────────

    @Transactional
    public byte[] trackOpen(UUID campaignContactId) {
        try {
            recordFirstEngagement(campaignContactId, "opened_at", "open_count");
        } catch (Exception e) {
            log.error("Failed to record open for campaignContactId={}: {}", campaignContactId, e.getMessage());
        }
        return TRANSPARENT_PIXEL;
    }

    @Transactional
    public String trackClick(UUID campaignContactId, String targetUrl) {
        String safeTarget = resolveSafeRedirect(campaignContactId, targetUrl);
        try {
            recordFirstEngagement(campaignContactId, "clicked_at", "click_count");
            recordFirstEngagement(campaignContactId, "opened_at", "open_count");
            if (!safeTarget.equals(frontendUrl)) recordLinkClick(campaignContactId, safeTarget);
        } catch (Exception e) {
            log.error("Failed to record click for campaignContactId={}: {}", campaignContactId, e.getMessage());
        }
        return safeTarget;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MktCampaign findCampaign(TenantId tenantId, UUID id) {
        return campaignRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", id.toString()));
    }

    /** Loads preferences for resolved ids in chunks; keeps only this tenant's contacts that are still opted in. */
    private List<MktContactPreference> loadOptedInPreferences(TenantId tenantId, List<UUID> ids) {
        List<MktContactPreference> out = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i += IN_CLAUSE_CHUNK) {
            preferenceRepo.findAllById(ids.subList(i, Math.min(i + IN_CLAUSE_CHUNK, ids.size()))).stream()
                    .filter(p -> tenantId.equals(p.getTenantId()) && p.isEmailOptedIn())
                    .forEach(out::add);
        }
        return out;
    }

    private void cascadeSuppressionToCampaigns(TenantId tenantId, String email, UUID preferenceId, String reasonText) {
        if (preferenceId != null) {
            jdbc.update("""
                UPDATE mkt_campaigns c
                SET unsubscribed_count = c.unsubscribed_count + s.n, updated_at = now()
                FROM (SELECT campaign_id, COUNT(*) AS n FROM mkt_campaign_contacts
                      WHERE preference_id = ? AND status = 'SENT' GROUP BY campaign_id) s
                WHERE c.id = s.campaign_id
                """, preferenceId);
            jdbc.update("UPDATE mkt_campaign_contacts SET status = 'UNSUBSCRIBED' "
                    + "WHERE preference_id = ? AND status = 'SENT'", preferenceId);

            jdbc.update("""
                UPDATE mkt_campaigns c
                SET suppressed_count = c.suppressed_count + s.n, updated_at = now()
                FROM (SELECT campaign_id, COUNT(*) AS n FROM mkt_campaign_contacts
                      WHERE preference_id = ? AND status = 'PENDING' GROUP BY campaign_id) s
                WHERE c.id = s.campaign_id
                """, preferenceId);
            jdbc.update("UPDATE mkt_campaign_contacts SET status = 'SUPPRESSED', error_message = ? "
                    + "WHERE preference_id = ? AND status = 'PENDING'", reasonText, preferenceId);
        }
        jdbc.update("UPDATE mkt_send_queue SET status = 'DEAD', error_message = ?, processed_at = now() "
                        + "WHERE tenant_id = ? AND to_email = ? AND status = 'PENDING'",
                reasonText, tenantId.getValue(), email);
    }

    private void grantCrmConsent(TenantId tenantId, MktContactPreference pref, String source) {
        if ("CUSTOMER".equals(pref.getEntityType()) && pref.getEntityId() != null) {
            try {
                crmFacade.recordMarketingConsentGranted(tenantId, pref.getEntityId(), source);
            } catch (Exception e) {
                log.error("Failed to record CRM consent for opt-in email={}: {}", pref.getEmail(), e.getMessage(), e);
            }
        }
    }

    private void syncOptOutToCrm(TenantId tenantId, MktContactPreference pref, String reason) {
        if ("CUSTOMER".equals(pref.getEntityType()) && pref.getEntityId() != null) {
            try {
                crmFacade.notifyMarketingConsentChanged(tenantId, pref.getEntityId(), false, null);
            } catch (Exception e) {
                log.error("Failed to record CRM activity for opt-out email={}: {}", pref.getEmail(), e.getMessage(), e);
            }
            try {
                crmFacade.withdrawMarketingConsent(tenantId, pref.getEntityId(), reason);
            } catch (Exception e) {
                log.error("Failed to withdraw CRM consent for opt-out email={}: {}", pref.getEmail(), e.getMessage(), e);
            }
        }
    }

    private void recordFirstEngagement(UUID campaignContactId, String column, String counterColumn) {
        if (!Set.of("opened_at", "clicked_at").contains(column)) {
            throw new IllegalArgumentException("Unsupported column " + column);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "UPDATE mkt_campaign_contacts SET " + column + " = now() "
                        + "WHERE id = ? AND " + column + " IS NULL RETURNING campaign_id",
                campaignContactId);
        if (!rows.isEmpty()) {
            bumpCampaignCounter((UUID) rows.get(0).get("campaign_id"), counterColumn, 1);
        }
    }

    /** One row per recipient per destination; repeat clicks bump a capped counter. Only allow-listed URLs get here. */
    private void recordLinkClick(UUID campaignContactId, String url) {
        if (url == null || url.length() > 2000) return;
        jdbc.update("""
            INSERT INTO mkt_link_clicks (id, tenant_id, campaign_id, campaign_contact_id, url, url_hash)
            SELECT ?, cc.tenant_id, cc.campaign_id, cc.id, CAST(? AS text), md5(CAST(? AS text))
            FROM mkt_campaign_contacts cc WHERE cc.id = ?
            ON CONFLICT (campaign_contact_id, url_hash)
            DO UPDATE SET click_count = LEAST(mkt_link_clicks.click_count + 1, 1000), last_clicked_at = now()
            """, UUID.randomUUID(), url, url, campaignContactId);
    }

    private void bumpCampaignCounter(UUID campaignId, String column, int by) {
        if (!COUNTER_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("Unsupported counter " + column);
        }
        jdbc.update("UPDATE mkt_campaigns SET " + column + " = " + column + " + ?, updated_at = now() WHERE id = ?",
                by, campaignId);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private Map<String, MktContactPreference> loadPreferencesByEmail(TenantId tenantId, Collection<String> emails) {
        Map<String, MktContactPreference> out = new HashMap<>();
        List<String> all = new ArrayList<>(emails);
        for (int i = 0; i < all.size(); i += IN_CLAUSE_CHUNK) {
            preferenceRepo.findByTenantIdAndEmailIn(tenantId, all.subList(i, Math.min(i + IN_CLAUSE_CHUNK, all.size())))
                    .forEach(p -> out.put(p.getEmail(), p));
        }
        return out;
    }

    private String resolveSafeRedirect(UUID campaignContactId, String targetUrl) {
        String fallback = frontendUrl;
        if (targetUrl == null || targetUrl.isBlank()) return fallback;

        URI target;
        try {
            target = URI.create(targetUrl.trim());
        } catch (Exception e) {
            return fallback;
        }
        String scheme = target.getScheme();
        String host   = target.getHost();
        if (host == null || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return fallback;
        }

        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT c.html_body FROM mkt_campaigns c "
                            + "JOIN mkt_campaign_contacts cc ON cc.campaign_id = c.id WHERE cc.id = ?",
                    campaignContactId);
            if (rows.isEmpty() || rows.get(0).get("html_body") == null) return fallback;

            Matcher m = HREF_PATTERN.matcher((String) rows.get(0).get("html_body"));
            while (m.find()) {
                try {
                    String allowedHost = URI.create(m.group(1)).getHost();
                    if (allowedHost != null && allowedHost.equalsIgnoreCase(host)) {
                        return target.toString();
                    }
                } catch (Exception ignored) { /* skip malformed href in template */ }
            }
        } catch (Exception e) {
            log.warn("Redirect validation failed for campaignContactId={}: {}", campaignContactId, e.getMessage());
        }
        log.warn("Blocked click redirect to host={} (not present in campaign links)", host);
        return fallback;
    }

    private String buildUnsubscribeConfirmationEmail(String tenantName, String name) {
        String greeting = name != null && !name.isBlank()
                ? "Hi " + HtmlUtils.htmlEscape(name.trim().split("\\s+")[0]) + "," : "Hi,";
        String safeTenant = HtmlUtils.htmlEscape(tenantName);
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#F1F5F9;margin:0;padding:0;">
              <div style="max-width:520px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <div style="background:#0D9488;padding:24px 28px;">
                  <h1 style="color:#fff;margin:0;font-size:18px">You've been unsubscribed</h1>
                </div>
                <div style="padding:28px;">
                  <p style="color:#374151;font-size:14px;line-height:1.6">%s</p>
                  <p style="color:#374151;font-size:14px;line-height:1.6">
                    You've been removed from %s's marketing email list and will no longer receive
                    promotional emails. This doesn't affect any transactional emails related to
                    services you've requested.
                  </p>
                  <p style="color:#94A3B8;font-size:12px;line-height:1.6;margin-top:20px;">
                    If this was a mistake, or you'd like to opt back in, please contact %s directly.
                  </p>
                </div>
              </div>
            </body></html>
            """.formatted(greeting, safeTenant, safeTenant);
    }

    private static String esc(boolean html, String s) {
        return html ? HtmlUtils.htmlEscape(s) : s;
    }

    /** Convenience overload without a campaign (no reply-to line in the footer). */
    String personalise(String template, MktContactPreference pref, String tenantName,
                       boolean isBody, UUID campaignContactId) {
        return personalise(template, pref, tenantName, isBody, campaignContactId, null);
    }

    String personalise(String template, MktContactPreference pref, String tenantName,
                       boolean isBody, UUID campaignContactId, MktCampaign campaign) {
        if (template == null) return "";
        String fullName  = pref.getName() != null ? pref.getName() : "";
        String firstName = !fullName.isBlank() ? fullName.trim().split("\\s+")[0] : "there";
        String unsubscribeUrl = unsubscribeBaseUrl() + pref.getUnsubscribeToken();

        String result = MarketingContentService.applyTokens(
                template, firstName, fullName, pref.getEmail(), tenantName, unsubscribeUrl, isBody);

        if (isBody) {
            if (campaignContactId != null) {
                result = rewriteLinksForClickTracking(result, campaignContactId);
            }

            if (!result.contains(unsubscribeUrl)) {
                String contact = campaign != null && campaign.getReplyTo() != null && !campaign.getReplyTo().isBlank()
                        ? " Contact: " + HtmlUtils.htmlEscape(campaign.getReplyTo()) + "." : "";
                result += "<br><hr><p style=\"font-size:11px;color:#94A3B8;text-align:center\">"
                        + "Sent by " + HtmlUtils.htmlEscape(tenantName) + "." + contact
                        + " You received this because you opted in to receive marketing emails. "
                        + "<a href=\"" + unsubscribeUrl + "\">Unsubscribe</a></p>";
            }

            if (campaignContactId != null) {
                result += "<img src=\"" + trackOpenBaseUrl() + campaignContactId
                        + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\" />";
            }
        }
        return result;
    }

    /** Renders a campaign's body for one recipient, with tracking and footer. Used by the journey engine. */
    public String renderBody(MktCampaign c, MktContactPreference pref, String tenantName, UUID campaignContactId) {
        return personalise(c.getHtmlBody(), pref, tenantName, true, campaignContactId, c);
    }

    public String renderSubject(MktCampaign c, MktContactPreference pref, String tenantName) {
        return personalise(c.getSubject(), pref, tenantName, false, null, c);
    }

    private String rewriteLinksForClickTracking(String html, UUID campaignContactId) {
        Matcher m = HREF_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String originalUrl = m.group(1);
            if (originalUrl.startsWith(unsubscribeBaseUrl())) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }
            String trackedUrl = trackClickBaseUrl() + campaignContactId
                    + "?url=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
            m.appendReplacement(sb, Matcher.quoteReplacement("href=\"" + trackedUrl + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.toLowerCase().trim();
    }

    private static boolean isValidEmail(String email) {
        return email != null && email.length() <= 320 && EMAIL_PATTERN.matcher(email).matches();
    }

    private static String cleanSource(String source) {
        if (source == null || source.isBlank()) return "MANUAL";
        String s = source.trim().toUpperCase();
        return s.length() > 50 ? s.substring(0, 50) : s;
    }

    private long countOptedOut(TenantId tenantId) {
        try {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mkt_contact_preferences WHERE tenant_id = ? AND email_opted_in = false",
                    Long.class, tenantId.getValue());
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    private long countCampaignsByStatus(TenantId tenantId, String status) {
        try {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mkt_campaigns WHERE tenant_id = ? AND status = ? AND deleted_at IS NULL AND kind = 'CAMPAIGN'",
                    Long.class, tenantId.getValue(), status);
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    private long countPendingQueueItems(TenantId tenantId) {
        try {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mkt_send_queue q " +
                            "JOIN mkt_campaigns c ON c.id = q.campaign_id " +
                            "WHERE c.tenant_id = ? AND q.status IN ('PENDING', 'SENDING')",
                    Long.class, tenantId.getValue());
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    /**
     * The tenant's own company name, used in {{company_name}} and the email footer. Falls back to a NEUTRAL label,
     * never the platform's own brand: a campaign email must not appear to come from HandyFlow itself.
     * Package-private so MarketingServiceEscapingTest can exercise it directly.
     */
    String fetchTenantName(TenantId tenantId) {
        return tenantFacade.findTenantDetails(tenantId)
                .map(TenantDetails::companyName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Our Team");
    }

    private CampaignResponse toCampaignResponse(MktCampaign c) {
        String templateName = c.getTemplateId() != null ? fetchTemplateName(c.getTemplateId()) : null;
        return new CampaignResponse(c.getId(), c.getName(), c.getChannel(),
                c.getTemplateId(), templateName, c.getSubject(),
                c.getAudienceType(), c.getSegmentId(), c.getSegmentName(), c.getStatus(),
                c.getScheduledAt(), c.getSentAt(),
                c.getRecipientCount(), c.getSentCount(),
                c.getBouncedCount(), c.getUnsubscribedCount(),
                c.getOpenCount(), c.getClickCount(), c.getSuppressedCount(),
                c.getDeliveredCount(), c.getComplainedCount(), c.getFailedCount(),
                c.getFromName(), c.getReplyTo(), c.getPauseReason(), c.getCreatedAt(),
                c.getReviewStatus(), c.getContentVersion(), c.getApprovedVersion(), c.getSentVersion(),
                userName(c.getSubmittedBy()), c.getSubmittedAt(),
                c.getSubmittedBy() != null && c.getSubmittedBy().equals(currentUserIdOrNull()),
                userName(c.getReviewedBy()), c.getReviewNote());
    }

    private String userName(UUID userId) {
        if (userId == null) return null;
        try {
            String n = jdbc.queryForObject(
                    "SELECT TRIM(COALESCE(first_name,'') || ' ' || COALESCE(last_name,'')) FROM users WHERE id = ?",
                    String.class, userId);
            return n == null || n.isBlank() ? null : n;
        } catch (Exception e) { return null; }
    }

    /** Null on scheduler threads where there is no logged-in user. */
    private UUID currentUserIdOrNull() {
        try { return TenantContext.getCurrentUserId(); } catch (Exception e) { return null; }
    }

    private String fetchTemplateName(UUID templateId) {
        try {
            return jdbc.queryForObject("SELECT name FROM mkt_templates WHERE id = ?",
                    String.class, templateId);
        } catch (Exception e) { return null; }
    }

    private TemplateResponse toTemplateResponse(MktTemplate t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getSubject(),
                t.getHtmlBody(), t.getPreviewText(), t.getCategory(), t.getCreatedAt());
    }

    /** suppressed is false here: a contact just opted in/imported is, by construction, not suppressed. */
    private ContactPreferenceResponse toPreferenceResponse(MktContactPreference p) {
        return new ContactPreferenceResponse(p.getId(), p.getEmail(), p.getName(),
                p.getEntityType(), p.getEntityId(), p.isEmailOptedIn(),
                p.getEmailOptedInAt(), p.getEmailOptedOutAt(),
                p.getOptInSource(), p.getCreatedAt(), false);
    }

    private SuppressionResponse toSuppressionResponse(MktSuppressionEntry s) {
        return new SuppressionResponse(s.getId(), s.getEmail(), s.getChannel(), s.getReason(),
                s.getSource(), s.getNote(), s.isActive(), s.getCreatedAt(),
                s.getLiftedAt(), s.getLiftReason());
    }
}