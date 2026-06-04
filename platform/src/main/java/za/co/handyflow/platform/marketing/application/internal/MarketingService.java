package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.domain.model.*;
import za.co.handyflow.platform.marketing.domain.repository.*;
import za.co.handyflow.platform.marketing.dto.*;
import za.co.handyflow.platform.shared.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    private static final String UNSUBSCRIBE_BASE_URL = "https://app.handyflow.co.za/unsubscribe/";

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MarketingSummaryResponse getSummary(TenantId tenantId) {
        long optedIn   = preferenceRepo.countOptedIn(tenantId);
        long optedOut  = countOptedOut(tenantId);
        long total     = optedIn + optedOut;                    // FIX: was calling countOptedIn twice
        long drafts    = countCampaignsByStatus(tenantId, "DRAFT");
        long sent      = countCampaignsByStatus(tenantId, "SENT");
        long scheduled = countCampaignsByStatus(tenantId, "SCHEDULED");
        // FIX: pending count was passing null as campaignId which is wrong for COUNT.
        // Count pending queue items across all campaigns for this tenant.
        long pending   = countPendingQueueItems(tenantId);
        return new MarketingSummaryResponse(total, optedIn, optedOut,
                drafts, sent, scheduled, pending);
    }

    // ── Contact preferences (POPIA) ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ContactPreferenceResponse> getContacts(TenantId tenantId, Pageable pageable) {
        return preferenceRepo.findAll(tenantId, pageable).map(this::toPreferenceResponse);
    }

    @Transactional
    public ContactPreferenceResponse optIn(TenantId tenantId, String email,
                                           String name, String source) {
        MktContactPreference pref = preferenceRepo
                .findByTenantIdAndEmail(tenantId, email.toLowerCase().trim())
                .orElseGet(() -> MktContactPreference.create(
                        tenantId, "SUBSCRIBER", null,
                        email, name, false, source));
        pref.optIn(source);
        preferenceRepo.save(pref);
        log.info("Opted in: email={} tenant={}", email, tenantId);
        return toPreferenceResponse(pref);
    }

    @Transactional
    public int importContacts(TenantId tenantId, ImportContactsRequest req) {
        int imported = 0;
        for (ImportContactsRequest.ContactEntry entry : req.contacts()) {
            String email = entry.email().toLowerCase().trim();
            MktContactPreference pref = preferenceRepo
                    .findByTenantIdAndEmail(tenantId, email)
                    .orElseGet(() -> MktContactPreference.create(
                            tenantId, "SUBSCRIBER", null,
                            email, entry.name(), false,
                            req.optInSource() != null ? req.optInSource() : "IMPORT"));
            if (entry.emailOptedIn()) pref.optIn(req.optInSource());
            preferenceRepo.save(pref);
            imported++;
        }
        log.info("Imported {} contacts for tenant={}", imported, tenantId);
        return imported;
    }

    @Transactional
    public int syncCrmContacts(TenantId tenantId) {
        List<java.util.Map<String, Object>> customers = jdbc.queryForList(
                "SELECT id, email, name FROM customers WHERE tenant_id = ? AND email IS NOT NULL AND deleted_at IS NULL",
                tenantId.getValue());

        int synced = 0;
        for (var row : customers) {
            String email = (String) row.get("email");
            if (email == null || email.isBlank()) continue;
            if (!preferenceRepo.existsByTenantIdAndEmail(tenantId, email.toLowerCase())) {
                MktContactPreference pref = MktContactPreference.create(
                        tenantId, "CUSTOMER", (UUID) row.get("id"),
                        email, (String) row.get("name"), false, "CRM_SYNC");
                preferenceRepo.save(pref);
                synced++;
            }
        }
        log.info("Synced {} CRM customers for tenant={}", synced, tenantId);
        return synced;
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

        MktCampaign campaign = MktCampaign.create(tenantId, req.name(), req.channel(),
                req.templateId(), subject, htmlBody, req.audienceType(), req.audienceFilter(),
                req.scheduledAt(), req.fromName(), req.replyTo(), createdBy);
        campaignRepo.save(campaign);
        log.info("Created campaign={} name={}", campaign.getId(), req.name());
        return toCampaignResponse(campaign);
    }

    @Transactional
    public CampaignResponse launchCampaign(TenantId tenantId, UUID id) {
        MktCampaign campaign = findCampaign(tenantId, id);
        if (!campaign.isDraft() && !"PAUSED".equals(campaign.getStatus())) {
            throw new HandyFlowException(
                    "Only DRAFT or PAUSED campaigns can be launched",
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        List<MktContactPreference> audience = preferenceRepo.findAllOptedIn(tenantId);
        if (audience.isEmpty()) {
            throw new HandyFlowException(
                    "No opted-in contacts found. Add contacts and collect opt-ins first.",
                    HttpStatus.BAD_REQUEST, "NO_AUDIENCE");
        }

        String tenantName = fetchTenantName(tenantId);
        campaign.startSending(audience.size());
        campaignRepo.save(campaign);

        for (MktContactPreference pref : audience) {
            // Skip if already in campaign (resuming after pause)
            if (campaignContactRepo.countByCampaignIdAndStatus(campaign.getId(), "PENDING") > 0) continue;

            MktCampaignContact cc = MktCampaignContact.create(
                    campaign.getId(), tenantId.getValue(),
                    pref.getEmail(), pref.getName(), pref.getId());
            campaignContactRepo.save(cc);

            String personalised = personalise(campaign.getHtmlBody(), pref, tenantName, true);
            String subject      = personalise(campaign.getSubject(),  pref, tenantName, false);

            MktSendQueue qItem = MktSendQueue.create(
                    campaign.getId(), cc.getId(), tenantId.getValue(),
                    pref.getEmail(), pref.getName(), subject, personalised,
                    campaign.getScheduledAt());
            sendQueueRepo.save(qItem);
        }

        log.info("Launched campaign={} recipients={}", id, audience.size());
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
        // Cancel pending queue items for this campaign
        jdbc.update("UPDATE mkt_send_queue SET status = 'DEAD' WHERE campaign_id = ? AND status = 'PENDING'",
                id);
        return toCampaignResponse(campaign);
    }

    // ── Send queue processor ──────────────────────────────────────────────────

    @Transactional
    public void processSendQueue() {
        List<MktSendQueue> batch = sendQueueRepo.findPendingBatch(
                Instant.now(), PageRequest.of(0, 50));
        if (batch.isEmpty()) return;
        log.info("Processing {} marketing emails", batch.size());

        for (MktSendQueue item : batch) {
            try {
                emailService.send(item.getToEmail(), item.getSubject(), item.getHtmlBody());
                item.markSent();
                sendQueueRepo.save(item);

                campaignContactRepo.findById(item.getCampaignContactId()).ifPresent(cc -> {
                    cc.markSent(); campaignContactRepo.save(cc);
                });
                campaignRepo.findById(item.getCampaignId()).ifPresent(c -> {
                    c.incrementSent(); campaignRepo.save(c);
                });

            } catch (Exception e) {
                log.error("Failed to send to={}: {}", item.getToEmail(), e.getMessage());
                item.markFailed(e.getMessage());
                sendQueueRepo.save(item);
                if (item.isDead()) {
                    campaignContactRepo.findById(item.getCampaignContactId()).ifPresent(cc -> {
                        cc.markBounced(e.getMessage()); campaignContactRepo.save(cc);
                    });
                    campaignRepo.findById(item.getCampaignId()).ifPresent(c -> {
                        c.incrementBounced(); campaignRepo.save(c);
                    });
                }
            }
        }
        markCompletedCampaigns();
    }

    @Transactional
    public void launchScheduledCampaigns() {
        campaignRepo.findScheduledReady(Instant.now()).forEach(c -> {
            try { launchCampaign(c.getTenantId(), c.getId()); }
            catch (Exception e) { log.error("Failed to auto-launch campaign={}: {}", c.getId(), e.getMessage()); }
        });
    }

    @Transactional
    public void handleUnsubscribe(String token) {
        preferenceRepo.findByUnsubscribeToken(token).ifPresentOrElse(pref -> {
            pref.optOut(); preferenceRepo.save(pref);
            // Also update the campaign contact status for audit
            jdbc.update(
                    "UPDATE mkt_campaign_contacts cc " +
                            "JOIN mkt_contact_preferences p ON p.id = cc.preference_id " +
                            "SET cc.status = 'UNSUBSCRIBED' " +
                            "WHERE p.unsubscribe_token = ? AND cc.status NOT IN ('UNSUBSCRIBED','BOUNCED')",
                    token);
            log.info("Unsubscribed: email={}", pref.getEmail());
        }, () -> log.warn("Unsubscribe token not found: {}", token));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MktCampaign findCampaign(TenantId tenantId, UUID id) {
        return campaignRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", id.toString()));
    }

    private String personalise(String template, MktContactPreference pref,
                               String tenantName) {
        return personalise(template, pref, tenantName, false);
    }

    private String personalise(String template, MktContactPreference pref,
                               String tenantName, boolean isBody) {
        if (template == null) return "";
        String firstName      = pref.getName() != null ? pref.getName().split(" ")[0] : "there";
        String unsubscribeUrl = UNSUBSCRIBE_BASE_URL + pref.getUnsubscribeToken();
        String result = template
                .replace("{{first_name}}",    firstName)
                .replace("{{name}}",          pref.getName() != null ? pref.getName() : "")
                .replace("{{email}}",         pref.getEmail())
                .replace("{{company_name}}",  tenantName)
                .replace("{{unsubscribe_url}}", unsubscribeUrl);
        // Only append the unsubscribe footer to the HTML body, never to the subject line
        if (isBody && !result.contains(unsubscribeUrl)) {
            result += "<br><hr><p style=\"font-size:11px;color:#94A3B8;text-align:center\">" +
                    "You received this because you opted in to receive marketing emails. " +
                    "<a href=\"" + unsubscribeUrl + "\">Unsubscribe</a></p>";
        }
        return result;
    }

    private void markCompletedCampaigns() {
        // Mark SENDING or PAUSED campaigns as SENT when no PENDING queue items remain.
        // Campaigns can end up PAUSED if the scheduler ran partially — we still mark
        // them SENT once all their queue items have been processed.
        jdbc.queryForList(
                        "SELECT DISTINCT c.id FROM mkt_campaigns c " +
                                "WHERE c.status IN ('SENDING','PAUSED') " +
                                "AND c.recipient_count > 0 " +
                                "AND NOT EXISTS (" +
                                "   SELECT 1 FROM mkt_send_queue q " +
                                "   WHERE q.campaign_id = c.id AND q.status = 'PENDING')")
                .forEach(row -> {
                    UUID cid = (UUID) row.get("id");
                    campaignRepo.findById(cid).ifPresent(c -> {
                        c.markSent(); campaignRepo.save(c);
                        log.info("Campaign={} fully sent — marked SENT", cid);
                    });
                });
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
                    "SELECT COUNT(*) FROM mkt_campaigns WHERE tenant_id = ? AND status = ? AND deleted_at IS NULL",
                    Long.class, tenantId.getValue(), status);
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    /**
     * FIX: original code passed null as campaignId to countByCampaignIdAndStatus()
     * which would return 0 or throw. Count pending items tenant-wide via jdbc instead.
     */
    private long countPendingQueueItems(TenantId tenantId) {
        try {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mkt_send_queue q " +
                            "JOIN mkt_campaigns c ON c.id = q.campaign_id " +
                            "WHERE c.tenant_id = ? AND q.status = 'PENDING'",
                    Long.class, tenantId.getValue());
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    private String fetchTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject("SELECT name FROM tenants WHERE id = ?",
                    String.class, tenantId.getValue());
        } catch (Exception e) { return "HandyFlow"; }
    }

    private CampaignResponse toCampaignResponse(MktCampaign c) {
        String templateName = c.getTemplateId() != null ? fetchTemplateName(c.getTemplateId()) : null;
        return new CampaignResponse(c.getId(), c.getName(), c.getChannel(),
                c.getTemplateId(), templateName, c.getSubject(),
                c.getAudienceType(), c.getStatus(),
                c.getScheduledAt(), c.getSentAt(),
                c.getRecipientCount(), c.getSentCount(),
                c.getBouncedCount(), c.getUnsubscribedCount(),
                c.getFromName(), c.getReplyTo(), c.getCreatedAt());
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

    private ContactPreferenceResponse toPreferenceResponse(MktContactPreference p) {
        return new ContactPreferenceResponse(p.getId(), p.getEmail(), p.getName(),
                p.getEntityType(), p.getEntityId(), p.isEmailOptedIn(),
                p.getEmailOptedInAt(), p.getEmailOptedOutAt(),
                p.getOptInSource(), p.getCreatedAt());
    }
}
