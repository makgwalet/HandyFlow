package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.domain.model.MktCampaign;
import za.co.handyflow.platform.marketing.domain.model.MktTemplate;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktTemplateRepository;
import za.co.handyflow.platform.marketing.dto.StudioDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Campaign editing, version history, approval workflow and the launch gate. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingLifecycleService {

    private final MktCampaignRepository     campaignRepo;
    private final MktTemplateRepository     templateRepo;
    private final MarketingSegmentService   segmentService;
    private final MarketingContentService   contentService;
    private final NamedParameterJdbcTemplate named;
    private final MarketingNotificationService notifications;

    // ── Settings ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MarketingSettings settings(TenantId tenantId) {
        List<MarketingSettings> rows = named.query(
                "SELECT require_approval, allow_self_approval FROM mkt_settings WHERE tenant_id = :t",
                new MapSqlParameterSource("t", tenantId.getValue()),
                (rs, i) -> new MarketingSettings(rs.getBoolean(1), rs.getBoolean(2)));
        return rows.isEmpty() ? new MarketingSettings(false, false) : rows.get(0);
    }

    @Transactional
    public MarketingSettings updateSettings(TenantId tenantId, UUID user, MarketingSettings req) {
        named.update("""
            INSERT INTO mkt_settings (tenant_id, require_approval, allow_self_approval, updated_by, updated_at)
            VALUES (:t, :r, :a, CAST(:u AS uuid), now())
            ON CONFLICT (tenant_id) DO UPDATE
            SET require_approval = :r, allow_self_approval = :a, updated_by = CAST(:u AS uuid), updated_at = now()
            """, new MapSqlParameterSource("t", tenantId.getValue()).addValue("r", req.requireApproval())
                .addValue("a", req.allowSelfApproval()).addValue("u", user == null ? null : user.toString()));
        log.info("Marketing settings updated tenant={} requireApproval={} by={}", tenantId, req.requireApproval(), user);
        return settings(tenantId);
    }

    // ── Versions ─────────────────────────────────────────────────────────────

    /** Writes an immutable snapshot of the campaign's current content. */
    public void recordVersion(MktCampaign c, String note, UUID user) {
        named.update("""
            INSERT INTO mkt_campaign_versions (campaign_id, version, tenant_id, subject, html_body, from_name, reply_to,
                audience_type, segment_id, segment_name, change_note, created_by)
            VALUES (:c, :v, :t, :s, :h, :f, :r, :at, :sid, :sn, :note, CAST(:u AS uuid))
            ON CONFLICT DO NOTHING
            """, new MapSqlParameterSource("c", c.getId()).addValue("v", c.getContentVersion())
                .addValue("t", c.getTenantId().getValue())
                .addValue("s", c.getSubject() == null ? "" : c.getSubject())
                .addValue("h", c.getHtmlBody() == null ? "" : c.getHtmlBody())
                .addValue("f", c.getFromName()).addValue("r", c.getReplyTo())
                .addValue("at", c.getAudienceType()).addValue("sid", c.getSegmentId()).addValue("sn", c.getSegmentName())
                .addValue("note", note).addValue("u", user == null ? null : user.toString()));
    }

    @Transactional(readOnly = true)
    public List<CampaignVersionResponse> listVersions(TenantId tenantId, UUID campaignId) {
        load(tenantId, campaignId);
        return queryVersions(tenantId, campaignId, null, false);
    }

    @Transactional(readOnly = true)
    public CampaignVersionResponse getVersion(TenantId tenantId, UUID campaignId, int version) {
        load(tenantId, campaignId);
        List<CampaignVersionResponse> rows = queryVersions(tenantId, campaignId, version, true);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Campaign version", String.valueOf(version));
        return rows.get(0);
    }

    private List<CampaignVersionResponse> queryVersions(TenantId tenantId, UUID campaignId, Integer version, boolean withBody) {
        MapSqlParameterSource p = new MapSqlParameterSource("c", campaignId).addValue("t", tenantId.getValue());
        String filter = "";
        if (version != null) { filter = " AND v.version = :v"; p.addValue("v", version); }
        return named.query("SELECT v.version, v.subject, v.html_body, v.from_name, v.reply_to, v.audience_type, v.segment_id, "
                        + "v.segment_name, v.change_note, v.created_at, "
                        + "NULLIF(TRIM(COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,'')), '') AS by_name "
                        + "FROM mkt_campaign_versions v LEFT JOIN users u ON u.id = v.created_by "
                        + "WHERE v.campaign_id = :c AND v.tenant_id = :t" + filter + " ORDER BY v.version DESC",
                p, (rs, i) -> new CampaignVersionResponse(rs.getInt("version"), rs.getString("subject"),
                        withBody ? rs.getString("html_body") : null, rs.getString("from_name"), rs.getString("reply_to"),
                        rs.getString("audience_type"), (UUID) rs.getObject("segment_id"), rs.getString("segment_name"),
                        rs.getString("change_note"), rs.getString("by_name"), rs.getTimestamp("created_at").toInstant()));
    }

    // ── Editing ──────────────────────────────────────────────────────────────

    @Transactional
    public void update(TenantId tenantId, UUID user, UUID id, UpdateCampaignRequest req) {
        MktCampaign c = load(tenantId, id);

        String audienceType = req.audienceType() == null ? "ALL_OPTED_IN" : req.audienceType().toUpperCase();
        UUID segmentId = null; String segmentName = null;
        if ("SEGMENT".equals(audienceType)) {
            segmentName = segmentService.requireActiveName(tenantId, req.segmentId());
            segmentId = req.segmentId();
        } else if (!"ALL_OPTED_IN".equals(audienceType)) {
            throw new HandyFlowException("Audience type '" + audienceType + "' is not supported.",
                    HttpStatus.BAD_REQUEST, "AUDIENCE_NOT_SUPPORTED");
        }

        String subject = req.subject(), body = req.htmlBody();
        if ((blank(subject) || blank(body)) && req.templateId() != null) {
            MktTemplate t = templateRepo.findByIdAndTenantId(req.templateId(), tenantId)
                    .orElseThrow(() -> new HandyFlowException("Template not found", HttpStatus.BAD_REQUEST, "NOT_FOUND"));
            if (blank(subject)) subject = t.getSubject();
            if (blank(body))    body    = t.getHtmlBody();
        }
        if (blank(subject) || blank(body)) {
            throw new HandyFlowException("Campaign requires a subject and content.", HttpStatus.BAD_REQUEST, "MISSING_CONTENT");
        }
        if (req.scheduledAt() != null && !req.scheduledAt().equals(c.getScheduledAt())
                && req.scheduledAt().isBefore(Instant.now().minusSeconds(60))) {
            throw new HandyFlowException("Scheduled time must be in the future", HttpStatus.BAD_REQUEST, "SCHEDULE_IN_PAST");
        }

        boolean bumped = c.applyEdit(req.name().trim(), req.templateId(), subject, body, audienceType, segmentId, segmentName,
                blankToNull(req.fromName()), blankToNull(req.replyTo()), req.scheduledAt());
        campaignRepo.save(c);
        if (bumped) {
            recordVersion(c, blankToNull(req.changeNote()) != null ? req.changeNote().trim() : "Edited", user);
            log.info("Campaign={} edited to version {} by={}", id, c.getContentVersion(), user);
        }
    }

    // ── Review workflow ──────────────────────────────────────────────────────

    @Transactional
    public void submit(TenantId tenantId, UUID user, UUID id) {
        MktCampaign c = load(tenantId, id);
        List<LintIssue> errors = contentService.lint(c.getSubject(), c.getHtmlBody()).stream()
                .filter(l -> "ERROR".equals(l.level())).toList();
        if (!errors.isEmpty()) throw lintBlock(errors);
        c.submitForReview(user);
        campaignRepo.save(c);
        notifications.submitted(tenantId, id, user);
    }

    @Transactional
    public void withdraw(TenantId tenantId, UUID id) {
        MktCampaign c = load(tenantId, id);
        c.withdrawReview();
        campaignRepo.save(c);
    }

    @Transactional
    public void approve(TenantId tenantId, UUID user, UUID id, String note) {
        MktCampaign c = load(tenantId, id);
        MarketingSettings s = settings(tenantId);
        if (s.requireApproval() && !s.allowSelfApproval() && user != null && user.equals(c.getSubmittedBy())) {
            throw new HandyFlowException("A different admin must approve a campaign you submitted. "
                    + "(An admin can allow self-approval in the approval settings.)", HttpStatus.CONFLICT, "FOUR_EYES_REQUIRED");
        }
        c.approve(user, blankToNull(note));
        campaignRepo.save(c);
        notifications.decided(tenantId, id, c.getSubmittedBy(), true, blankToNull(note));
        log.info("Campaign={} version {} approved by={}", id, c.getContentVersion(), user);
    }

    @Transactional
    public void requestChanges(TenantId tenantId, UUID user, UUID id, String note) {
        if (note == null || note.trim().length() < 5) {
            throw new HandyFlowException("Explain what needs to change (at least 5 characters).", HttpStatus.BAD_REQUEST, "NOTE_REQUIRED");
        }
        MktCampaign c = load(tenantId, id);
        c.requestChanges(user, note.trim());
        campaignRepo.save(c);
        notifications.decided(tenantId, id, c.getSubmittedBy(), false, note.trim());
    }

    // ── Launch gate (called by MarketingService.launchCampaign) ──────────────

    @Transactional(readOnly = true)
    public void assertLaunchAllowed(TenantId tenantId, MktCampaign c) {
        List<LintIssue> errors = contentService.lint(c.getSubject(), c.getHtmlBody()).stream()
                .filter(l -> "ERROR".equals(l.level())).toList();
        if (!errors.isEmpty()) throw lintBlock(errors);

        if (!settings(tenantId).requireApproval()) return;
        if (!"APPROVED".equals(c.getReviewStatus()) || !Objects.equals(c.getApprovedVersion(), c.getContentVersion())) {
            throw new HandyFlowException("This campaign must be approved before it can be sent. Submit this version for review.",
                    HttpStatus.CONFLICT, "APPROVAL_REQUIRED");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MktCampaign load(TenantId tenantId, UUID id) {
        return campaignRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", id.toString()));
    }

    private static HandyFlowException lintBlock(List<LintIssue> errors) {
        return new HandyFlowException("Fix these content problems first: "
                + errors.stream().map(LintIssue::message).collect(Collectors.joining(" ")),
                HttpStatus.BAD_REQUEST, "CONTENT_LINT_ERRORS");
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return blank(s) ? null : s.trim(); }
}