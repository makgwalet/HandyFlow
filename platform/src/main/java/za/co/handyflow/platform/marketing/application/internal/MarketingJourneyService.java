package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.domain.model.MktCampaign;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignRepository;
import za.co.handyflow.platform.marketing.dto.AnalyticsDtos.CampaignMetrics;
import za.co.handyflow.platform.marketing.dto.JourneyDtos.*;
import za.co.handyflow.platform.marketing.dto.StudioDtos.LintIssue;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Journey CRUD, lifecycle (activate / pause / resume / archive / clone), stats and enrolment views. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingJourneyService {

    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper               om;
    private final MarketingSegmentService    segmentService;
    private final MarketingContentService    contentService;
    private final MarketingAnalyticsService  analytics;
    private final MktCampaignRepository      campaignRepo;

    static final Set<String> TRIGGERS = Set.of("OPTED_IN", "SEGMENT_ENTRY", "OPT_IN_ANNIVERSARY");
    private static final int MAX_EMAILS = 10;

    private record Row(UUID id, String name, String description, String trigger, UUID segmentId, String segmentName,
                       String status, String stepsJson, Integer reentryDays, int gap, int ws, int we, boolean sw,
                       Instant activatedAt, Instant createdAt, Instant updatedAt, long active, long completed, long exited,
                       UUID topicId, String topicName) {}

    private record Prepared(String trigger, UUID segmentId, String segmentName, String stepsJson,
                            Integer reentry, int gap, int ws, int we, boolean sw, UUID topicId) {}

    private static final String SELECT = """
        SELECT j.id, j.name, j.description, j.trigger_type, j.segment_id, j.segment_name, j.status,
               CAST(j.steps AS text) AS steps_json, j.reentry_days, j.min_gap_hours, j.window_start_hour,
               j.window_end_hour, j.skip_weekends, j.activated_at, j.created_at, j.updated_at, j.topic_id,
               (SELECT tp.name FROM mkt_topics tp WHERE tp.id = j.topic_id) AS topic_name,
               (SELECT COUNT(*) FROM mkt_journey_enrollments e WHERE e.journey_id = j.id AND e.status = 'ACTIVE') AS n_active,
               (SELECT COUNT(*) FROM mkt_journey_enrollments e WHERE e.journey_id = j.id AND e.status = 'COMPLETED') AS n_completed,
               (SELECT COUNT(*) FROM mkt_journey_enrollments e WHERE e.journey_id = j.id AND e.status IN ('EXITED','CANCELLED')) AS n_exited
        FROM mkt_journeys j
        """;

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JourneyResponse> list(TenantId t) {
        return named.query(SELECT + " WHERE j.tenant_id = :t AND j.deleted_at IS NULL AND j.status <> 'ARCHIVED' ORDER BY j.created_at DESC",
                new MapSqlParameterSource("t", t.getValue()), (rs, i) -> toResponse(row(rs)));
    }

    @Transactional(readOnly = true)
    public JourneyResponse get(TenantId t, UUID id) { return toResponse(load(t, id)); }

    // ── Writes ───────────────────────────────────────────────────────────────

    @Transactional
    public JourneyResponse create(TenantId t, UUID user, JourneyRequest req) {
        Prepared p = prepare(t, req);
        UUID id = UUID.randomUUID();
        named.update("""
            INSERT INTO mkt_journeys (id, tenant_id, name, description, trigger_type, segment_id, segment_name, status, steps,
                reentry_days, min_gap_hours, window_start_hour, window_end_hour, skip_weekends, topic_id, created_by, updated_by)
            VALUES (:id, :t, :n, CAST(:d AS text), :tt, :sid, CAST(:sn AS text), 'DRAFT', CAST(:steps AS jsonb),
                :rd, :gap, :ws, :we, :sw, :tp, CAST(:u AS uuid), CAST(:u AS uuid))
            """, params(t, id, req.name(), req.description(), p).addValue("u", user == null ? null : user.toString()));
        return get(t, id);
    }

    /** Full edit: DRAFT only. Steps, trigger and segment are frozen once a journey has been activated. */
    @Transactional
    public JourneyResponse update(TenantId t, UUID user, UUID id, JourneyRequest req) {
        Row j = load(t, id);
        if (!"DRAFT".equals(j.status())) {
            throw new HandyFlowException("Steps can only be edited while a journey is a draft. Clone it to make a changed version.",
                    HttpStatus.CONFLICT, "JOURNEY_FROZEN");
        }
        Prepared p = prepare(t, req);
        named.update("""
            UPDATE mkt_journeys SET name = :n, description = CAST(:d AS text), trigger_type = :tt, segment_id = :sid,
                segment_name = CAST(:sn AS text), steps = CAST(:steps AS jsonb), reentry_days = :rd, min_gap_hours = :gap,
                window_start_hour = :ws, window_end_hour = :we, skip_weekends = :sw, topic_id = :tp, updated_by = CAST(:u AS uuid), updated_at = now()
            WHERE id = :id AND tenant_id = :t
            """, params(t, id, req.name(), req.description(), p).addValue("u", user == null ? null : user.toString()));
        return get(t, id);
    }

    /** Name, description, pacing and send window: editable in any non-archived state. */
    @Transactional
    public JourneyResponse updateSettings(TenantId t, UUID user, UUID id, JourneySettingsRequest req) {
        Row j = load(t, id);
        if ("ARCHIVED".equals(j.status())) throw new HandyFlowException("Archived journeys can't be changed.", HttpStatus.CONFLICT, "JOURNEY_ARCHIVED");
        int gap = req.minGapHours() == null ? j.gap() : req.minGapHours();
        int ws = req.windowStartHour() == null ? j.ws() : req.windowStartHour();
        int we = req.windowEndHour() == null ? j.we() : req.windowEndHour();
        checkPacing(gap, ws, we);
        named.update("""
            UPDATE mkt_journeys SET name = :n, description = CAST(:d AS text), min_gap_hours = :gap, window_start_hour = :ws,
                window_end_hour = :we, skip_weekends = :sw, updated_by = CAST(:u AS uuid), updated_at = now()
            WHERE id = :id AND tenant_id = :t
            """, new MapSqlParameterSource("id", id).addValue("t", t.getValue()).addValue("n", req.name().trim())
                .addValue("d", blank(req.description())).addValue("gap", gap).addValue("ws", ws).addValue("we", we)
                .addValue("sw", req.skipWeekends() == null ? j.sw() : req.skipWeekends())
                .addValue("u", user == null ? null : user.toString()));
        return get(t, id);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Transactional
    public ActivationPreview previewActivation(TenantId t, UUID id) {
        Row j = load(t, id);
        if (!"DRAFT".equals(j.status())) throw new HandyFlowException("Only a draft can be activated.", HttpStatus.CONFLICT, "JOURNEY_STATE");
        List<LintIssue> issues = lintSteps(parse(j.stepsJson()));
        long now; String note;
        switch (j.trigger()) {
            case "SEGMENT_ENTRY" -> {
                now = segmentService.previewSaved(t, j.segmentId()).eligible();
                note = now + " eligible contact(s) in \"" + j.segmentName() + "\" will be enrolled as soon as you activate, then new matches every 5 minutes.";
            }
            case "OPT_IN_ANNIVERSARY" -> {
                now = anniversaryCount(t);
                note = now + " contact(s) have their opt-in anniversary today. After that, contacts are enrolled on their anniversary each year.";
            }
            default -> { now = 0; note = "Nobody is enrolled now. Contacts are enrolled when they opt in after activation."; }
        }
        return new ActivationPreview(now, note, issues);
    }

    @Transactional
    public JourneyResponse activate(TenantId t, UUID user, UUID id) {
        Row j = load(t, id);
        if (!"DRAFT".equals(j.status())) throw new HandyFlowException("Only a draft can be activated.", HttpStatus.CONFLICT, "JOURNEY_STATE");
        List<StepDto> steps = parse(j.stepsJson());
        List<LintIssue> errors = lintSteps(steps).stream().filter(l -> "ERROR".equals(l.level())).toList();
        if (!errors.isEmpty()) {
            throw new HandyFlowException("Fix these content problems first: " + String.join(" ", errors.stream().map(LintIssue::message).toList()),
                    HttpStatus.BAD_REQUEST, "CONTENT_LINT_ERRORS");
        }
        if ("SEGMENT_ENTRY".equals(j.trigger())) segmentService.requireActiveName(t, j.segmentId());

        List<StepDto> out = new ArrayList<>();
        for (StepDto s : steps) {
            if (!"EMAIL".equals(s.type())) { out.add(s); continue; }
            MktCampaign c = MktCampaign.create(t, "Journey: " + j.name() + " — " + s.name(), "EMAIL", null,
                    s.subject(), s.htmlBody(), "ALL_OPTED_IN", null, null, null, null, s.fromName(), s.replyTo(), user);
            c.startSending(0);
            c.markJourney();
            campaignRepo.saveAndFlush(c);
            out.add(new StepDto(s.type(), s.name(), s.subject(), s.htmlBody(), s.fromName(), s.replyTo(), null, null, null, c.getId()));
        }
        named.update("UPDATE mkt_journeys SET steps = CAST(:s AS jsonb), status = 'ACTIVE', activated_at = now(), updated_at = now(), "
                        + "updated_by = CAST(:u AS uuid) WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("s", write(out)).addValue("id", id).addValue("t", t.getValue())
                        .addValue("u", user == null ? null : user.toString()));
        log.info("Journey={} activated tenant={} by={}", id, t, user);
        return get(t, id);
    }

    @Transactional
    public JourneyResponse pause(TenantId t, UUID id) { return transition(t, id, "ACTIVE", "PAUSED"); }

    @Transactional
    public JourneyResponse resume(TenantId t, UUID id) { return transition(t, id, "PAUSED", "ACTIVE"); }

    @Transactional
    public JourneyResponse archive(TenantId t, UUID id) {
        Row j = load(t, id);
        named.update("UPDATE mkt_journeys SET status = 'ARCHIVED', updated_at = now() WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("id", id).addValue("t", t.getValue()));
        named.update("UPDATE mkt_journey_enrollments SET status = 'CANCELLED', exit_reason = 'Journey archived', completed_at = now(), locked_at = NULL "
                + "WHERE journey_id = :id AND status = 'ACTIVE'", new MapSqlParameterSource("id", j.id()));
        return get(t, id);
    }

    @Transactional
    public JourneyResponse clone(TenantId t, UUID user, UUID id) {
        Row j = load(t, id);
        List<StepDto> fresh = parse(j.stepsJson()).stream()
                .map(s -> new StepDto(s.type(), s.name(), s.subject(), s.htmlBody(), s.fromName(), s.replyTo(), s.amount(), s.unit(),
                        s.condition(), null, s.yesTo(), s.noTo())).toList();
        UUID nid = UUID.randomUUID();
        named.update("""
            INSERT INTO mkt_journeys (id, tenant_id, name, description, trigger_type, segment_id, segment_name, status, steps,
                reentry_days, min_gap_hours, window_start_hour, window_end_hour, skip_weekends, topic_id, created_by, updated_by)
            VALUES (:id, :t, :n, CAST(:d AS text), :tt, :sid, CAST(:sn AS text), 'DRAFT', CAST(:steps AS jsonb),
                :rd, :gap, :ws, :we, :sw, :tp, CAST(:u AS uuid), CAST(:u AS uuid))
            """, new MapSqlParameterSource("id", nid).addValue("t", t.getValue()).addValue("n", trunc("Copy of " + j.name(), 150))
                .addValue("d", j.description()).addValue("tt", j.trigger()).addValue("sid", j.segmentId()).addValue("sn", j.segmentName())
                .addValue("steps", write(fresh)).addValue("rd", j.reentryDays(), java.sql.Types.INTEGER).addValue("gap", j.gap())
                .addValue("ws", j.ws()).addValue("we", j.we()).addValue("sw", j.sw()).addValue("tp", j.topicId())
                .addValue("u", user == null ? null : user.toString()));
        return get(t, nid);
    }

    /** Resumes any step campaigns the delivery engine auto-paused (bounce/complaint thresholds). */
    @Transactional
    public int resumePausedSteps(TenantId t, UUID id) {
        int n = 0;
        for (StepDto s : parse(load(t, id).stepsJson())) {
            if (s.campaignId() == null) continue;
            MktCampaign c = campaignRepo.findByIdAndTenantId(s.campaignId(), t).orElse(null);
            if (c != null && "PAUSED".equals(c.getStatus())) { c.resume(); campaignRepo.save(c); n++; }
        }
        return n;
    }

    // ── Stats and enrolments ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public JourneyStats stats(TenantId t, UUID id) {
        Row j = load(t, id);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("ACTIVE", "COMPLETED", "EXITED", "CANCELLED")) byStatus.put(s, 0L);
        named.query("SELECT status, COUNT(*) AS n FROM mkt_journey_enrollments WHERE journey_id = :j GROUP BY status",
                new MapSqlParameterSource("j", id), rs -> { byStatus.put(rs.getString("status"), rs.getLong("n")); });

        Map<Integer, Long> waiting = new HashMap<>();
        named.query("SELECT current_step, COUNT(*) AS n FROM mkt_journey_enrollments WHERE journey_id = :j AND status = 'ACTIVE' GROUP BY current_step",
                new MapSqlParameterSource("j", id), rs -> { waiting.put(rs.getInt("current_step"), rs.getLong("n")); });

        List<StepStat> out = new ArrayList<>();
        List<StepDto> steps = parse(j.stepsJson());
        for (int i = 0; i < steps.size(); i++) {
            StepDto s = steps.get(i);
            CampaignMetrics m = null; String cStatus = null, pause = null;
            if (s.campaignId() != null) {
                try { m = analytics.campaign(t, s.campaignId()); } catch (ResourceNotFoundException ignored) { }
                List<Map<String, Object>> r = named.queryForList("SELECT status, pause_reason FROM mkt_campaigns WHERE id = :c AND tenant_id = :t",
                        new MapSqlParameterSource("c", s.campaignId()).addValue("t", t.getValue()));
                if (!r.isEmpty()) { cStatus = (String) r.get(0).get("status"); pause = (String) r.get(0).get("pause_reason"); }
            }
            out.add(new StepStat(i, s.type(), summary(s), s.campaignId(), cStatus, pause, waiting.getOrDefault(i, 0L), m));
        }
        return new JourneyStats(byStatus, out);
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentRow> enrollments(TenantId t, UUID id, String status, String search, Pageable pageable) {
        load(t, id);
        MapSqlParameterSource p = new MapSqlParameterSource("j", id).addValue("t", t.getValue());
        StringBuilder w = new StringBuilder("e.journey_id = :j AND e.tenant_id = :t");
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            String s = status.trim().toUpperCase();
            if (!Set.of("ACTIVE", "COMPLETED", "EXITED", "CANCELLED").contains(s)) throw new HandyFlowException("Unknown status filter.", HttpStatus.BAD_REQUEST, "BAD_FILTER");
            p.addValue("st", s); w.append(" AND e.status = :st");
        }
        if (search != null && !search.isBlank()) {
            p.addValue("q", "%" + search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            w.append(" AND LOWER(e.email) LIKE :q ESCAPE '\\'");
        }
        int size = Math.max(1, Math.min(pageable.getPageSize(), 100)), page = Math.max(0, pageable.getPageNumber());
        Long total = named.queryForObject("SELECT COUNT(*) FROM mkt_journey_enrollments e WHERE " + w, p, Long.class);
        MapSqlParameterSource q = new MapSqlParameterSource(); q.addValues(p.getValues());
        q.addValue("limit", size).addValue("offset", (long) page * size);
        List<EnrollmentRow> rows = named.query("SELECT e.id, e.preference_id, e.email, pr.name, e.status, e.current_step, e.next_run_at, "
                        + "e.enrolled_at, e.completed_at, e.exit_reason, e.last_error FROM mkt_journey_enrollments e "
                        + "LEFT JOIN mkt_contact_preferences pr ON pr.id = e.preference_id WHERE " + w
                        + " ORDER BY e.enrolled_at DESC, e.id LIMIT :limit OFFSET :offset", q,
                (rs, i) -> new EnrollmentRow((UUID) rs.getObject("id"), (UUID) rs.getObject("preference_id"), rs.getString("email"),
                        rs.getString("name"), rs.getString("status"), rs.getInt("current_step"), ts(rs.getTimestamp("next_run_at")),
                        ts(rs.getTimestamp("enrolled_at")), ts(rs.getTimestamp("completed_at")), rs.getString("exit_reason"), rs.getString("last_error")));
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<LogEntry> log(TenantId t, UUID journeyId, UUID enrollmentId) {
        load(t, journeyId);
        return named.query("SELECT step_index, step_type, outcome, detail, created_at FROM mkt_journey_step_log "
                        + "WHERE enrollment_id = :e AND journey_id = :j AND tenant_id = :t ORDER BY created_at, step_index",
                new MapSqlParameterSource("e", enrollmentId).addValue("j", journeyId).addValue("t", t.getValue()),
                (rs, i) -> new LogEntry(rs.getInt("step_index"), rs.getString("step_type"), rs.getString("outcome"),
                        rs.getString("detail"), ts(rs.getTimestamp("created_at"))));
    }

    @Transactional
    public void cancelEnrollment(TenantId t, UUID journeyId, UUID enrollmentId) {
        load(t, journeyId);
        int n = named.update("UPDATE mkt_journey_enrollments SET status = 'CANCELLED', exit_reason = 'Removed by staff', completed_at = now(), locked_at = NULL "
                        + "WHERE id = :e AND journey_id = :j AND tenant_id = :t AND status = 'ACTIVE'",
                new MapSqlParameterSource("e", enrollmentId).addValue("j", journeyId).addValue("t", t.getValue()));
        if (n == 0) throw new HandyFlowException("That enrolment is not active.", HttpStatus.CONFLICT, "ENROLLMENT_STATE");
    }

    // ── Validation ───────────────────────────────────────────────────────────

    private Prepared prepare(TenantId t, JourneyRequest r) {
        String trigger = r.triggerType().trim().toUpperCase();
        if (!TRIGGERS.contains(trigger)) throw bad("Unknown trigger '" + r.triggerType() + "'.");
        UUID segId = null; String segName = null;
        if ("SEGMENT_ENTRY".equals(trigger)) {
            segName = segmentService.requireActiveName(t, r.segmentId());
            segId = r.segmentId();
        }
        List<StepDto> steps = normaliseSteps(r.steps());
        Integer re = r.reentryDays();
        if (re != null && (re < 1 || re > 3650)) throw bad("Re-entry must be between 1 and 3650 days, or empty for never.");
        int gap = r.minGapHours() == null ? 24 : r.minGapHours();
        int ws = r.windowStartHour() == null ? 8 : r.windowStartHour();
        int we = r.windowEndHour() == null ? 17 : r.windowEndHour();
        checkPacing(gap, ws, we);
        requireTopic(t, r.topicId());
        return new Prepared(trigger, segId, segName, write(steps), re, gap, ws, we, r.skipWeekends() == null || r.skipWeekends(), r.topicId());
    }

    private static void checkPacing(int gap, int ws, int we) {
        if (gap < 0 || gap > 720) throw bad("Minimum gap must be between 0 and 720 hours.");
        if (ws < 0 || ws > 23 || we < 1 || we > 24 || ws >= we) throw bad("The send window must start before it ends (hours 0–24, South African time).");
    }

    private List<StepDto> normaliseSteps(List<StepDto> in) {
        List<StepDto> out = new ArrayList<>();
        int emails = 0;
        for (int i = 0; i < in.size(); i++) {
            StepDto s = in.get(i);
            String type = s.type() == null ? "" : s.type().trim().toUpperCase();
            String at = "Step " + (i + 1) + ": ";
            switch (type) {
                case "EMAIL" -> {
                    emails++;
                    if (blank(s.subject()) == null) throw bad(at + "the email needs a subject.");
                    if (blank(s.htmlBody()) == null) throw bad(at + "the email needs content.");
                    if (s.subject().length() > 500) throw bad(at + "the subject is too long.");
                    if (s.htmlBody().length() > 600_000) throw bad(at + "the email is too large.");
                    if (blank(s.replyTo()) != null && !s.replyTo().contains("@")) throw bad(at + "the reply-to address looks invalid.");
                    out.add(new StepDto("EMAIL", trunc(blank(s.name()) != null ? s.name().trim() : "Email " + emails, 100),
                            s.subject().trim(), s.htmlBody(), blank(s.fromName()), blank(s.replyTo()), null, null, null, null));
                }
                case "WAIT" -> {
                    String unit = s.unit() == null ? "" : s.unit().trim().toUpperCase();
                    if (!Set.of("HOURS", "DAYS").contains(unit)) throw bad(at + "wait unit must be HOURS or DAYS.");
                    int max = "DAYS".equals(unit) ? 365 : 720;
                    if (s.amount() == null || s.amount() < 1 || s.amount() > max) throw bad(at + "wait must be between 1 and " + max + " " + unit.toLowerCase() + ".");
                    out.add(new StepDto("WAIT", null, null, null, null, null, s.amount(), unit, null, null));
                }
                case "EXIT_IF" -> {
                    String c = condition(s.condition(), at);
                    if (emails == 0) throw bad(at + "an exit check must come after at least one email.");
                    out.add(new StepDto("EXIT_IF", null, null, null, null, null, null, null, c, null));
                }
                case "BRANCH" -> {
                    String c = condition(s.condition(), at);
                    if (emails == 0) throw bad(at + "a branch must come after at least one email.");
                    out.add(new StepDto("BRANCH", null, null, null, null, null, null, null, c, null, s.yesTo(), s.noTo()));
                }
                default -> throw bad(at + "unknown step type '" + s.type() + "'.");
            }
        }
        if (emails == 0) throw bad("A journey needs at least one email.");
        if (emails > MAX_EMAILS) throw bad("A journey can have at most " + MAX_EMAILS + " emails.");
        if (!"EMAIL".equals(out.get(out.size() - 1).type())) throw bad("The last step must be an email.");
        for (int i = 0; i < out.size(); i++) {      // branches only jump FORWARD (or end the journey), so a journey always finishes
            StepDto b = out.get(i);
            if (!"BRANCH".equals(b.type())) continue;
            checkTarget(b.yesTo(), i, out.size());
            checkTarget(b.noTo(), i, out.size());
        }
        return out;
    }

    private List<LintIssue> lintSteps(List<StepDto> steps) {
        List<LintIssue> out = new ArrayList<>();
        for (StepDto s : steps) {
            if (!"EMAIL".equals(s.type())) continue;
            for (LintIssue l : contentService.lint(s.subject(), s.htmlBody())) {
                if ("INFO".equals(l.level())) continue;
                out.add(new LintIssue(l.level(), l.code(), "\"" + s.name() + "\": " + l.message()));
            }
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JourneyResponse transition(TenantId t, UUID id, String from, String to) {
        Row j = load(t, id);
        if (!from.equals(j.status())) throw new HandyFlowException("Journey is " + j.status() + ", not " + from + ".", HttpStatus.CONFLICT, "JOURNEY_STATE");
        named.update("UPDATE mkt_journeys SET status = :to, updated_at = now() WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("to", to).addValue("id", id).addValue("t", t.getValue()));
        return get(t, id);
    }

    private long anniversaryCount(TenantId t) {
        Long n = named.queryForObject("SELECT COUNT(*) FROM mkt_contact_preferences p WHERE p.tenant_id = :t AND p.email_opted_in = true "
                + "AND p.email_opted_in_at < now() - interval '300 days' "
                + "AND to_char(p.email_opted_in_at AT TIME ZONE 'Africa/Johannesburg', 'MM-DD') = to_char(now() AT TIME ZONE 'Africa/Johannesburg', 'MM-DD') "
                + "AND NOT " + MarketingAudienceService.SUPPRESSED_SQL, new MapSqlParameterSource("t", t.getValue()), Long.class);
        return n == null ? 0 : n;
    }

    private MapSqlParameterSource params(TenantId t, UUID id, String name, String desc, Prepared p) {
        return new MapSqlParameterSource("id", id).addValue("t", t.getValue()).addValue("n", name.trim()).addValue("d", blank(desc))
                .addValue("tt", p.trigger()).addValue("sid", p.segmentId()).addValue("sn", p.segmentName()).addValue("steps", p.stepsJson())
                .addValue("rd", p.reentry(), java.sql.Types.INTEGER).addValue("gap", p.gap()).addValue("ws", p.ws())
                .addValue("we", p.we()).addValue("sw", p.sw()).addValue("tp", p.topicId());
    }

    private Row load(TenantId t, UUID id) {
        List<Row> rows = named.query(SELECT + " WHERE j.id = :id AND j.tenant_id = :t AND j.deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("t", t.getValue()), (rs, i) -> row(rs));
        if (rows.isEmpty()) throw new ResourceNotFoundException("Journey", id.toString());
        return rows.get(0);
    }

    private Row row(ResultSet rs) throws SQLException {
        return new Row((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("description"), rs.getString("trigger_type"),
                (UUID) rs.getObject("segment_id"), rs.getString("segment_name"), rs.getString("status"), rs.getString("steps_json"),
                (Integer) rs.getObject("reentry_days"), rs.getInt("min_gap_hours"), rs.getInt("window_start_hour"), rs.getInt("window_end_hour"),
                rs.getBoolean("skip_weekends"), ts(rs.getTimestamp("activated_at")), ts(rs.getTimestamp("created_at")),
                ts(rs.getTimestamp("updated_at")), rs.getLong("n_active"), rs.getLong("n_completed"), rs.getLong("n_exited"),
                (UUID) rs.getObject("topic_id"), rs.getString("topic_name"));
    }

    private JourneyResponse toResponse(Row r) {
        return new JourneyResponse(r.id(), r.name(), r.description(), r.trigger(), r.segmentId(), r.segmentName(), r.status(),
                parse(r.stepsJson()), r.reentryDays(), r.gap(), r.ws(), r.we(), r.sw(), r.activatedAt(),
                r.active(), r.completed(), r.exited(), r.createdAt(), r.updatedAt(), r.topicId(), r.topicName());
    }

    List<StepDto> parse(String json) {
        try { return om.readValue(json, new TypeReference<List<StepDto>>() {}); }
        catch (Exception e) { throw new HandyFlowException("This journey's steps could not be read.", HttpStatus.CONFLICT, "JOURNEY_CORRUPT"); }
    }

    private String write(List<StepDto> steps) {
        try { return om.writeValueAsString(steps); }
        catch (Exception e) { throw new IllegalStateException("Could not serialise steps", e); }
    }

    private static String summary(StepDto s) {
        return switch (s.type()) {
            case "WAIT" -> "Wait " + s.amount() + " " + s.unit().toLowerCase();
            case "EXIT_IF" -> "Exit if the contact " + conditionText(s.condition());
            case "BRANCH" -> "If the contact " + conditionText(s.condition()) + ": " + target(s.yesTo(), "continue") + "; otherwise " + target(s.noTo(), "continue");
            default -> s.name() + " — " + s.subject();
        };
    }

    private static void checkTarget(Integer to, int from, int size) {
        if (to == null || to == -1) return;
        if (to <= from || to >= size) {
            throw bad("Step " + (from + 1) + ": a branch can only jump forward to a later step, or end the journey.");
        }
    }

    private static String condition(String raw, String at) {
        String c = raw == null ? "" : raw.trim().toUpperCase();
        if (!Set.of("CLICKED", "OPENED", "PURCHASED").contains(c)) throw bad(at + "condition must be CLICKED, OPENED or PURCHASED.");
        return c;
    }

    static String conditionText(String c) {
        return switch (c == null ? "" : c) {
            case "PURCHASED" -> "has paid since joining";
            case "CLICKED"   -> "clicked an earlier email";
            default          -> "opened an earlier email";
        };
    }

    private static String target(Integer to, String otherwise) {
        return to == null ? otherwise : to == -1 ? "end the journey" : "go to step " + (to + 1);
    }

    /** The topic must be one of THIS tenant's live topics. */
    private void requireTopic(TenantId t, UUID topicId) {
        if (topicId == null) return;
        Long n = named.queryForObject("SELECT COUNT(*) FROM mkt_topics WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL",
                new MapSqlParameterSource("id", topicId).addValue("t", t.getValue()), Long.class);
        if (n == null || n == 0) throw bad("That topic no longer exists.");
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "JOURNEY_INVALID"); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String trunc(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }
    private static Instant ts(Timestamp t) { return t == null ? null : t.toInstant(); }
}