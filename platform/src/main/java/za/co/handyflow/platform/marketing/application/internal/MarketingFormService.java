package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.domain.model.MktCampaign;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignRepository;
import za.co.handyflow.platform.marketing.dto.FormDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** Staff-side management of topics and signup forms, plus the topic helpers used by campaigns. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingFormService {

    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper               om;
    private final MktCampaignRepository      campaignRepo;

    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    // ── Topics ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TopicResponse> topics(TenantId t) {
        return named.query("""
            SELECT t.id, t.name, t.description, t.visible,
                   (SELECT COUNT(*) FROM mkt_contact_topic_prefs p WHERE p.topic_id = t.id AND p.subscribed = false) AS opted_out,
                   (SELECT COUNT(*) FROM mkt_campaigns c WHERE c.topic_id = t.id AND c.deleted_at IS NULL
                      AND c.status IN ('DRAFT','SCHEDULED','SENDING','PAUSED')) AS used
            FROM mkt_topics t WHERE t.tenant_id = :t AND t.deleted_at IS NULL ORDER BY lower(t.name)
            """, new MapSqlParameterSource("t", t.getValue()),
                (rs, i) -> new TopicResponse((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("description"),
                        rs.getBoolean("visible"), rs.getLong("opted_out"), rs.getLong("used")));
    }

    @Transactional
    public TopicResponse createTopic(TenantId t, UUID user, TopicRequest req) {
        UUID id = UUID.randomUUID();
        try {
            named.update("INSERT INTO mkt_topics (id, tenant_id, name, description, visible, created_by) "
                            + "VALUES (:id, :t, :n, :d, :v, CAST(:u AS uuid))",
                    new MapSqlParameterSource("id", id).addValue("t", t.getValue()).addValue("n", req.name().trim())
                            .addValue("d", blank(req.description())).addValue("v", req.visible() == null || req.visible())
                            .addValue("u", user == null ? null : user.toString()));
        } catch (DuplicateKeyException e) { throw nameTaken(); }
        return topic(t, id);
    }

    @Transactional
    public TopicResponse updateTopic(TenantId t, UUID id, TopicRequest req) {
        int n;
        try {
            n = named.update("UPDATE mkt_topics SET name = :n, description = :d, visible = :v, updated_at = now() "
                            + "WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL",
                    new MapSqlParameterSource("id", id).addValue("t", t.getValue()).addValue("n", req.name().trim())
                            .addValue("d", blank(req.description())).addValue("v", req.visible() == null || req.visible()));
        } catch (DuplicateKeyException e) { throw nameTaken(); }
        if (n == 0) throw new ResourceNotFoundException("Topic", id.toString());
        return topic(t, id);
    }

    @Transactional
    public void deleteTopic(TenantId t, UUID id) {
        TopicResponse tp = topic(t, id);
        if (tp.usedByActiveCampaigns() > 0) {
            throw new HandyFlowException("This topic is used by " + tp.usedByActiveCampaigns()
                    + " unfinished campaign(s). Cancel or finish them first.", HttpStatus.CONFLICT, "TOPIC_IN_USE");
        }
        named.update("UPDATE mkt_topics SET deleted_at = now() WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("id", id).addValue("t", t.getValue()));
    }

    private TopicResponse topic(TenantId t, UUID id) {
        return topics(t).stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Topic", id.toString()));
    }

    // ── Campaign helpers ─────────────────────────────────────────────────────

    /** Preference ids that explicitly opted out of the topic. */
    @Transactional(readOnly = true)
    public Set<UUID> topicOptedOut(TenantId t, UUID topicId) {
        return new HashSet<>(named.queryForList("SELECT preference_id FROM mkt_contact_topic_prefs "
                        + "WHERE tenant_id = :t AND topic_id = :p AND subscribed = false",
                new MapSqlParameterSource("t", t.getValue()).addValue("p", topicId), UUID.class));
    }

    public String topicName(UUID topicId) {
        if (topicId == null) return null;
        try { return named.queryForObject("SELECT name FROM mkt_topics WHERE id = :id", new MapSqlParameterSource("id", topicId), String.class); }
        catch (Exception e) { return null; }
    }

    @Transactional
    public void setCampaignTopic(TenantId t, UUID campaignId, UUID topicId) {
        MktCampaign c = campaignRepo.findByIdAndTenantId(campaignId, t)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId.toString()));
        if (topicId != null) topic(t, topicId);   // 404 if not this tenant's active topic
        c.changeTopic(topicId);                   // 409 unless DRAFT/SCHEDULED
        campaignRepo.save(c);
    }

    // ── Forms ────────────────────────────────────────────────────────────────

    private static final String FORM_SELECT = """
        SELECT f.id, f.slug, f.name, f.title, f.description, f.button_label, f.consent_text, f.success_message,
               f.collect_name, CAST(f.topic_ids AS text) AS topic_ids, f.status, f.created_at,
               (SELECT COUNT(*) FROM mkt_optin_requests r WHERE r.form_id = f.id) AS requests,
               (SELECT COUNT(*) FROM mkt_optin_requests r WHERE r.form_id = f.id AND r.confirmed_at IS NOT NULL) AS confirmed
        FROM mkt_forms f
        """;

    /** Signup-form submissions as CSV (admin export): who signed up, via which form, when, whether confirmed, and the consent wording shown. Max 50,000 rows. */
    @Transactional(readOnly = true)
    public String exportSubmissionsCsv(TenantId t, UUID formId) {
        MapSqlParameterSource p = new MapSqlParameterSource("t", t.getValue());
        String formFilter = "";
        if (formId != null) {
            Integer n = named.queryForObject("SELECT COUNT(*) FROM mkt_forms WHERE id = :f AND tenant_id = :t",
                    new MapSqlParameterSource("f", formId).addValue("t", t.getValue()), Integer.class);
            if (n == null || n == 0) throw new ResourceNotFoundException("Form", formId.toString());
            formFilter = " AND r.form_id = :f";
            p.addValue("f", formId);
        }
        StringBuilder sb = new StringBuilder("Form,Email,Name,Submitted at,Confirmed at,Status,Consent text\r\n");
        named.query("SELECT f.name AS form_name, r.email, r.name, r.created_at, r.confirmed_at, r.expires_at, r.consent_text "
                        + "FROM mkt_optin_requests r JOIN mkt_forms f ON f.id = r.form_id AND f.tenant_id = r.tenant_id "
                        + "WHERE r.tenant_id = :t" + formFilter + " ORDER BY r.created_at DESC, r.id LIMIT 50000", p,
                rs -> {
                    java.sql.Timestamp conf = rs.getTimestamp("confirmed_at");
                    java.sql.Timestamp exp = rs.getTimestamp("expires_at");
                    String status = conf != null ? "CONFIRMED"
                            : exp != null && exp.toInstant().isBefore(java.time.Instant.now()) ? "EXPIRED" : "PENDING";
                    java.sql.Timestamp created = rs.getTimestamp("created_at");
                    sb.append(String.join(",",
                            MarketingAudienceService.cell(rs.getString("form_name")), MarketingAudienceService.cell(rs.getString("email")),
                            MarketingAudienceService.cell(rs.getString("name")),
                            MarketingAudienceService.cell(created == null ? "" : created.toInstant().toString()),
                            MarketingAudienceService.cell(conf == null ? "" : conf.toInstant().toString()),
                            MarketingAudienceService.cell(status), MarketingAudienceService.cell(rs.getString("consent_text"))))
                            .append("\r\n");
                });
        return sb.toString();
    }

    /** Deletes opt-in requests that were never confirmed and expired more than {@code graceDays} ago. Returns the number removed. */
    @Transactional
    public int purgeExpiredOptInRequests(int graceDays) {
        return named.update("DELETE FROM mkt_optin_requests WHERE confirmed_at IS NULL AND expires_at < NOW() - make_interval(days => :d)",
                new MapSqlParameterSource("d", Math.max(graceDays, 1)));
    }

    @Transactional(readOnly = true)
    public List<FormResponse> forms(TenantId t) {
        return named.query(FORM_SELECT + " WHERE f.tenant_id = :t AND f.deleted_at IS NULL ORDER BY f.created_at DESC",
                new MapSqlParameterSource("t", t.getValue()), (rs, i) -> mapForm(rs));
    }

    @Transactional
    public FormResponse createForm(TenantId t, UUID user, FormRequest req) {
        List<UUID> topicIds = cleanTopics(t, req.topicIds());
        for (int attempt = 0; attempt < 5; attempt++) {
            UUID id = UUID.randomUUID();
            try {
                named.update("""
                    INSERT INTO mkt_forms (id, tenant_id, slug, name, title, description, button_label, consent_text,
                        success_message, collect_name, topic_ids, created_by)
                    VALUES (:id, :t, :slug, :n, :ti, :d, :b, :c, :s, :cn, CAST(:topics AS jsonb), CAST(:u AS uuid))
                    """, params(t, id, req, topicIds).addValue("slug", slug()).addValue("u", user == null ? null : user.toString()));
                return form(t, id);
            } catch (DuplicateKeyException e) { /* slug collision: retry */ }
        }
        throw new IllegalStateException("Could not allocate a form address");
    }

    @Transactional
    public FormResponse updateForm(TenantId t, UUID id, FormRequest req) {
        form(t, id);
        int n = named.update("""
            UPDATE mkt_forms SET name = :n, title = :ti, description = :d, button_label = :b, consent_text = :c,
                success_message = :s, collect_name = :cn, topic_ids = CAST(:topics AS jsonb), updated_at = now()
            WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL
            """, params(t, id, req, cleanTopics(t, req.topicIds())));
        if (n == 0) throw new ResourceNotFoundException("Form", id.toString());
        return form(t, id);
    }

    @Transactional
    public FormResponse setFormStatus(TenantId t, UUID id, String status) {
        form(t, id);
        named.update("UPDATE mkt_forms SET status = :s, updated_at = now() WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("s", status).addValue("id", id).addValue("t", t.getValue()));
        return form(t, id);
    }

    private FormResponse form(TenantId t, UUID id) {
        List<FormResponse> rows = named.query(FORM_SELECT + " WHERE f.id = :id AND f.tenant_id = :t AND f.deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("t", t.getValue()), (rs, i) -> mapForm(rs));
        if (rows.isEmpty()) throw new ResourceNotFoundException("Form", id.toString());
        return rows.get(0);
    }

    private MapSqlParameterSource params(TenantId t, UUID id, FormRequest r, List<UUID> topicIds) {
        String consent = r.consentText().trim();
        return new MapSqlParameterSource("id", id).addValue("t", t.getValue()).addValue("n", r.name().trim())
                .addValue("ti", r.title().trim()).addValue("d", blank(r.description()))
                .addValue("b", blank(r.buttonLabel()) == null ? "Subscribe" : r.buttonLabel().trim())
                .addValue("c", consent).addValue("s", blank(r.successMessage()))
                .addValue("cn", r.collectName() == null || r.collectName()).addValue("topics", json(topicIds));
    }

    private List<UUID> cleanTopics(TenantId t, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        List<UUID> found = named.queryForList("SELECT id FROM mkt_topics WHERE tenant_id = :t AND deleted_at IS NULL AND id IN (:ids)",
                new MapSqlParameterSource("t", t.getValue()).addValue("ids", distinct), UUID.class);
        if (found.size() != distinct.size()) {
            throw new HandyFlowException("One of the selected topics no longer exists.", HttpStatus.BAD_REQUEST, "TOPIC_UNKNOWN");
        }
        return distinct;
    }

    private FormResponse mapForm(ResultSet rs) throws SQLException {
        List<UUID> topics;
        try { topics = om.readValue(rs.getString("topic_ids"), new TypeReference<List<UUID>>() {}); }
        catch (Exception e) { topics = List.of(); }
        return new FormResponse((UUID) rs.getObject("id"), rs.getString("slug"), rs.getString("name"), rs.getString("title"),
                rs.getString("description"), rs.getString("button_label"), rs.getString("consent_text"), rs.getString("success_message"),
                rs.getBoolean("collect_name"), topics, rs.getString("status"), rs.getLong("requests"), rs.getLong("confirmed"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String json(Object o) {
        try { return om.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String slug() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
        return sb.toString();
    }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static HandyFlowException nameTaken() {
        return new HandyFlowException("A topic with this name already exists.", HttpStatus.CONFLICT, "TOPIC_NAME_EXISTS");
    }
}