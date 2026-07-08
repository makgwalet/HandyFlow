package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.marketing.domain.model.*;
import za.co.handyflow.platform.marketing.domain.repository.*;
import za.co.handyflow.platform.marketing.dto.*;
import za.co.handyflow.platform.shared.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // FIX: was `private static final String UNSUBSCRIBE_BASE_URL =
    // "https://app.handyflow.co.za/unsubscribe/"` — hardcoded to production
    // regardless of which environment actually sent the email. Non-final,
    // Spring-@Value-injected fields instead — deliberately NOT part of the
    // Lombok @RequiredArgsConstructor-generated constructor (that only
    // covers the final repository/service fields above), so these get
    // populated via plain field injection instead, avoiding any ambiguity
    // between Lombok's own @Value (for immutable value classes) and
    // Spring's @Value (for property injection) — this class imports
    // Spring's explicitly.
    @Value("${app.frontend.url:https://app.handyflow.co.za}")
    private String frontendUrl;

    // Tracking pixel/click-redirect URLs are loaded directly by an email
    // client, not through the SPA's own configured API client — they need
    // a real, absolute, publicly-reachable URL to the backend API itself,
    // which may or may not be the same domain as frontendUrl depending on
    // deployment. Defaults to the same domain (a common reverse-proxy setup
    // where /api/** is routed to the backend) but should be set explicitly
    // per environment if that's not how this is actually deployed.
    @Value("${app.api.public-url:https://app.handyflow.co.za}")
    private String apiPublicUrl;

    private String unsubscribeBaseUrl() { return frontendUrl + "/unsubscribe/"; }
    private String trackOpenBaseUrl()   { return apiPublicUrl + "/api/v1/marketing/track/open/"; }
    private String trackClickBaseUrl()  { return apiPublicUrl + "/api/v1/marketing/track/click/"; }

    // 1x1 transparent GIF — the smallest valid GIF byte sequence, the
    // standard technique for an email open-tracking pixel.
    private static final byte[] TRANSPARENT_PIXEL = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
            (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            0x21, (byte) 0xF9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
    };

    private static final Pattern HREF_PATTERN = Pattern.compile("href=\"(https?://[^\"]+)\"");

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
        boolean wasAlreadyOptedIn = pref.isEmailOptedIn();
        pref.optIn(source);
        preferenceRepo.save(pref);

        // NEW: grant the formal POPIA consent record, not just the
        // Marketing-local flag — only for CUSTOMER-type contacts (a real
        // CRM record exists), and only on a genuine new opt-in rather than
        // re-confirming an already-active one (avoids piling up duplicate
        // marketing-only consent records every time this is called on an
        // already-opted-in contact).
        if ("CUSTOMER".equals(pref.getEntityType()) && pref.getEntityId() != null && !wasAlreadyOptedIn) {
            try {
                crmFacade.recordMarketingConsentGranted(tenantId, pref.getEntityId(), source);
            } catch (Exception e) {
                log.error("Failed to record CRM consent for opt-in email={}: {}",
                        pref.getEmail(), e.getMessage(), e);
            }
        }

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
            boolean wasAlreadyOptedIn = pref.isEmailOptedIn();
            if (entry.emailOptedIn()) pref.optIn(req.optInSource());
            preferenceRepo.save(pref);

            // Same CRM consent grant as optIn() above, same reasoning —
            // only for CUSTOMER-type contacts, only on a genuine new
            // opt-in.
            if (entry.emailOptedIn() && "CUSTOMER".equals(pref.getEntityType())
                    && pref.getEntityId() != null && !wasAlreadyOptedIn) {
                try {
                    crmFacade.recordMarketingConsentGranted(tenantId, pref.getEntityId(), req.optInSource());
                } catch (Exception e) {
                    log.error("Failed to record CRM consent for imported contact email={}: {}",
                            pref.getEmail(), e.getMessage(), e);
                }
            }

            imported++;
        }
        log.info("Imported {} contacts for tenant={}", imported, tenantId);
        return imported;
    }

    @Transactional
    public int syncCrmContacts(TenantId tenantId) {
        // FIX: was raw jdbc SQL directly against `customers` — confirmed
        // bypassing CRM's own anti-corruption boundary (CrmFacade), exactly
        // the coupling problem the original module review flagged. This
        // wasn't previously possible to fix properly: CrmFacade only
        // exposed single-customer lookups (findCustomerById,
        // customerExists), nothing for enumerating the whole customer base.
        // findActiveCustomersWithEmail(...) is the new facade method this
        // needed — see CrmFacade.java.
        List<CustomerSummary> customers = crmFacade.findActiveCustomersWithEmail(tenantId);

        // Same N+1 fix as before, just now operating on the facade's DTO
        // instead of a raw JDBC row map.
        List<String> candidateEmails = customers.stream()
                .map(CustomerSummary::email)
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.toLowerCase().trim())
                .distinct()
                .toList();
        Set<String> alreadyExists = candidateEmails.isEmpty()
                ? Set.of()
                : new HashSet<>(preferenceRepo.findExistingEmails(tenantId, candidateEmails));

        // STILL NOT DONE HERE: syncing every CRM customer with an email,
        // rather than filtering by tags/segments/marketing-consent status
        // as the original review also recommended. That's a genuinely
        // separate feature (audience targeting), not a coupling bug — worth
        // its own pass once there's a clearer picture of what CRM exposes
        // for tags/segments specifically.
        int synced = 0;
        for (CustomerSummary customer : customers) {
            String email = customer.email();
            if (email == null || email.isBlank()) continue;
            String normalizedEmail = email.toLowerCase().trim();
            if (!alreadyExists.contains(normalizedEmail)) {
                MktContactPreference pref = MktContactPreference.create(
                        tenantId, "CUSTOMER", customer.id(),
                        email, customer.name(), false, "CRM_SYNC");
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

            String personalised = personalise(campaign.getHtmlBody(), pref, tenantName, true, cc.getId());
            String subject      = personalise(campaign.getSubject(),  pref, tenantName, false, null);

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
            pref.optOut();
            preferenceRepo.save(pref);

            // FIX: was "UPDATE mkt_campaign_contacts cc JOIN mkt_contact_preferences p
            // ON ... SET ..." — MySQL's UPDATE...JOIN syntax. PostgreSQL doesn't support
            // JOIN in an UPDATE statement at all; every migration in this module uses
            // Postgres-specific features (gen_random_uuid(), JSONB), so this would throw
            // a syntax error the first time anyone actually clicked an unsubscribe link —
            // the exact mechanism POPIA and CAN-SPAM require to work. PostgreSQL's
            // equivalent is UPDATE ... SET ... FROM ... WHERE.
            jdbc.update(
                    "UPDATE mkt_campaign_contacts cc " +
                            "SET status = 'UNSUBSCRIBED' " +
                            "FROM mkt_contact_preferences p " +
                            "WHERE p.id = cc.preference_id " +
                            "AND p.unsubscribe_token = ? " +
                            "AND cc.status NOT IN ('UNSUBSCRIBED', 'BOUNCED')",
                    token);

            // NEW: previously nobody told the recipient their unsubscribe actually
            // went through — a confirmation is part of "unsubscribe mechanism
            // end-to-end", not an optional extra. Wrapped so an email failure can
            // never roll back the unsubscribe itself, which is the part that
            // actually has to succeed — same defensive pattern used for every
            // other notification added across this project.
            try {
                String tenantName = fetchTenantName(pref.getTenantId());
                emailService.send(pref.getEmail(), "You've been unsubscribed",
                        buildUnsubscribeConfirmationEmail(tenantName, pref.getName()));
            } catch (Exception e) {
                log.error("Failed to send unsubscribe confirmation to={}: {}",
                        pref.getEmail(), e.getMessage(), e);
            }

            // NEW: was previously not done at all — no facade method existed
            // for this, so a POPIA-relevant opt-out never reached CRM's
            // activity timeline, regardless of the raw-SQL question. Only
            // meaningful for CUSTOMER-type contacts with a real entityId —
            // standalone SUBSCRIBER-type contacts were never in CRM to begin
            // with. Wrapped for the same reason as the confirmation email
            // above: a CRM-side failure must never affect the unsubscribe
            // itself, which already succeeded by this point.
            if ("CUSTOMER".equals(pref.getEntityType()) && pref.getEntityId() != null) {
                try {
                    crmFacade.notifyMarketingConsentChanged(
                            pref.getTenantId(), pref.getEntityId(), false, null);
                } catch (Exception e) {
                    log.error("Failed to record CRM activity for unsubscribe email={}: {}",
                            pref.getEmail(), e.getMessage(), e);
                }
                // NEW: the actual POPIA-compliance record, not just the
                // timeline entry above — withdraws the customer's
                // marketing-only consent (never touches any broader
                // consent record, see CrmFacade's Javadoc for why).
                try {
                    crmFacade.withdrawMarketingConsent(
                            pref.getTenantId(), pref.getEntityId(), "Unsubscribed via email link");
                } catch (Exception e) {
                    log.error("Failed to withdraw CRM consent for unsubscribe email={}: {}",
                            pref.getEmail(), e.getMessage(), e);
                }
            }

            log.info("Unsubscribed: email={}", pref.getEmail());
        }, () -> log.warn("Unsubscribe token not found: {}", token));
    }

    // ── Open/click tracking (public, no auth — fired by an email client) ─────

    /**
     * Called by the tracking pixel <img> tag. Always returns the pixel
     * bytes regardless of whether the campaignContactId is valid — a
     * missing/invalid pixel image is exactly the kind of thing that looks
     * broken to a recipient, so this fails silently rather than ever
     * surfacing an error through what's supposed to be an invisible image.
     */
    @Transactional
    public byte[] trackOpen(UUID campaignContactId) {
        try {
            campaignContactRepo.findById(campaignContactId).ifPresent(cc -> {
                if (cc.markOpenedIfFirstTime()) {
                    campaignContactRepo.save(cc);
                    campaignRepo.findById(cc.getCampaignId()).ifPresent(c -> {
                        c.incrementOpened();
                        campaignRepo.save(c);
                    });
                }
            });
        } catch (Exception e) {
            log.error("Failed to record open for campaignContactId={}: {}", campaignContactId, e.getMessage());
        }
        return TRANSPARENT_PIXEL;
    }

    /**
     * Called when a recipient clicks a tracked link. Returns the original
     * destination URL for the controller to redirect to — this method never
     * throws for an unknown/invalid campaignContactId, since failing to
     * record a click must never stop the recipient actually reaching the
     * link they clicked.
     */
    @Transactional
    public String trackClick(UUID campaignContactId, String targetUrl) {
        try {
            campaignContactRepo.findById(campaignContactId).ifPresent(cc -> {
                if (cc.markClickedIfFirstTime()) {
                    campaignContactRepo.save(cc);
                    campaignRepo.findById(cc.getCampaignId()).ifPresent(c -> {
                        c.incrementClicked();
                        campaignRepo.save(c);
                    });
                }
                // A click implies an open even if the tracking pixel's image
                // never loaded — many email clients block remote images by
                // default but still let links through, which would otherwise
                // undercount opens for exactly the recipients most likely to
                // have actually engaged with the email.
                if (cc.markOpenedIfFirstTime()) {
                    campaignContactRepo.save(cc);
                    campaignRepo.findById(cc.getCampaignId()).ifPresent(c -> {
                        c.incrementOpened();
                        campaignRepo.save(c);
                    });
                }
            });
        } catch (Exception e) {
            log.error("Failed to record click for campaignContactId={}: {}", campaignContactId, e.getMessage());
        }
        return targetUrl;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MktCampaign findCampaign(TenantId tenantId, UUID id) {
        return campaignRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", id.toString()));
    }

    private String buildUnsubscribeConfirmationEmail(String tenantName, String name) {
        String greeting = name != null && !name.isBlank()
                ? "Hi " + name.split(" ")[0] + "," : "Hi,";
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
            """.formatted(greeting, tenantName, tenantName);
    }

    private String personalise(String template, MktContactPreference pref,
                               String tenantName) {
        return personalise(template, pref, tenantName, false, null);
    }

    /**
     * @param campaignContactId only meaningful (and only used) when isBody
     *                          is true — click/open tracking is per
     *                          recipient-per-campaign, and the subject line
     *                          has no links or pixel to inject into.
     */
    private String personalise(String template, MktContactPreference pref,
                               String tenantName, boolean isBody, UUID campaignContactId) {
        if (template == null) return "";
        String firstName      = pref.getName() != null ? pref.getName().split(" ")[0] : "there";
        String unsubscribeUrl = unsubscribeBaseUrl() + pref.getUnsubscribeToken();
        String result = template
                .replace("{{first_name}}",    firstName)
                .replace("{{name}}",          pref.getName() != null ? pref.getName() : "")
                .replace("{{email}}",         pref.getEmail())
                .replace("{{company_name}}",  tenantName)
                .replace("{{unsubscribe_url}}", unsubscribeUrl);

        if (isBody) {
            // NEW: rewrite existing links to route through the click-tracking
            // redirect endpoint BEFORE the unsubscribe footer is appended
            // below — that ordering is what keeps the unsubscribe link
            // itself from ever being wrapped in click tracking, with no
            // special-casing needed. campaignContactId can be null if this
            // is ever called outside the normal launch flow (defensively
            // skips tracking rather than injecting a broken URL).
            if (campaignContactId != null) {
                result = rewriteLinksForClickTracking(result, campaignContactId);
            }

            // Only append the unsubscribe footer to the HTML body, never to the subject line
            if (!result.contains(unsubscribeUrl)) {
                result += "<br><hr><p style=\"font-size:11px;color:#94A3B8;text-align:center\">" +
                        "You received this because you opted in to receive marketing emails. " +
                        "<a href=\"" + unsubscribeUrl + "\">Unsubscribe</a></p>";
            }

            // NEW: the open-tracking pixel — appended last, after the
            // unsubscribe footer, so it doesn't interfere with anything
            // else being appended to the body.
            if (campaignContactId != null) {
                result += "<img src=\"" + trackOpenBaseUrl() + campaignContactId
                        + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\" />";
            }
        }
        return result;
    }

    // NEW: rewrites every http(s) link in the HTML body to route through
    // the click-tracking redirect endpoint first. Regex-based rather than a
    // full HTML parser — matches this codebase's existing approach to HTML
    // manipulation (e.g. the contract PDF generator's tag stripping) rather
    // than adding a new parsing dependency. Covers the standard case of
    // plain href="https://..." links, which is what the template system
    // actually generates; genuinely unusual hand-written HTML (relative
    // URLs, javascript: links, links split across multiple attributes)
    // isn't rewritten and will just work as a normal, untracked link
    // instead of breaking.
    private String rewriteLinksForClickTracking(String html, UUID campaignContactId) {
        Matcher m = HREF_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String originalUrl = m.group(1);
            String trackedUrl = trackClickBaseUrl() + campaignContactId
                    + "?url=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
            m.appendReplacement(sb, Matcher.quoteReplacement("href=\"" + trackedUrl + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
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
                c.getOpenCount(), c.getClickCount(),
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