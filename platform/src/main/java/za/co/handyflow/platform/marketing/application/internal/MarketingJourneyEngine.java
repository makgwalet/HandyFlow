package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.marketing.domain.model.MktCampaign;
import za.co.handyflow.platform.marketing.domain.model.MktCampaignContact;
import za.co.handyflow.platform.marketing.domain.model.MktContactPreference;
import za.co.handyflow.platform.marketing.domain.model.MktSendQueue;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignContactRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktContactPreferenceRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktSendQueueRepository;
import za.co.handyflow.platform.marketing.dto.JourneyDtos.StepDto;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Runs journeys: enrols contacts per trigger, then advances due enrolments one step at a time.
 * Each enrolment step is its own transaction, so queueing an email and advancing the step are atomic.
 * Emails go through the normal delivery engine via hidden per-step campaigns, so consent is re-checked
 * again at send time, and tracking, unsubscribe and provider events all behave as for any campaign.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingJourneyEngine {

    private final NamedParameterJdbcTemplate     named;
    private final TransactionTemplate            tx;
    private final ObjectMapper                   om;
    private final MarketingSegmentService        segmentService;
    private final MarketingService               marketing;
    private final MarketingConsentService        consentService;
    private final MktContactPreferenceRepository prefRepo;
    private final MktCampaignRepository          campaignRepo;
    private final MktCampaignContactRepository   ccRepo;
    private final MktSendQueueRepository         queueRepo;

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int ENROLL_CAP = 500;
    private static final int PROCESS_CAP = 200;
    private static final int MAX_STEPS_PER_RUN = 25;
    private static final int MAX_ERRORS = 5;

    // ── Enrolment ────────────────────────────────────────────────────────────

    @Scheduled(cron = "0 */5 * * * *", zone = "Africa/Johannesburg")
    public void enrollTick() {
        List<Map<String, Object>> journeys = named.queryForList(
                "SELECT id, tenant_id, trigger_type, segment_id, reentry_days, activated_at FROM mkt_journeys "
                        + "WHERE status = 'ACTIVE' AND deleted_at IS NULL", Map.of());
        for (Map<String, Object> j : journeys) {
            try {
                int n = enroll(j);
                if (n > 0) log.info("Journey={} enrolled {} contact(s)", j.get("id"), n);
            } catch (Exception e) {
                log.error("Journey={} enrolment failed: {}", j.get("id"), e.getMessage(), e);
            }
        }
    }

    private int enroll(Map<String, Object> j) {
        UUID jid = (UUID) j.get("id"), tenant = (UUID) j.get("tenant_id"), segmentId = (UUID) j.get("segment_id");
        String trigger = (String) j.get("trigger_type");
        Integer rd = (Integer) j.get("reentry_days");
        if ("OPT_IN_ANNIVERSARY".equals(trigger) && rd == null) rd = 300;   // never re-enrol the same year
        Instant activated = ((Timestamp) j.get("activated_at")).toInstant();

        String insert = "INSERT INTO mkt_journey_enrollments (id, tenant_id, journey_id, preference_id, email, status, current_step, next_run_at) "
                + "SELECT gen_random_uuid(), p.tenant_id, :j, p.id, p.email, 'ACTIVE', 0, now() FROM mkt_contact_preferences p "
                + "WHERE p.tenant_id = :t AND p.email_opted_in = true";
        String guard = " AND NOT " + MarketingAudienceService.SUPPRESSED_SQL
                + " AND NOT EXISTS (SELECT 1 FROM mkt_journey_enrollments e WHERE e.journey_id = :j AND e.preference_id = p.id "
                + "AND (e.status = 'ACTIVE' OR CAST(:rd AS integer) IS NULL OR e.enrolled_at > now() - make_interval(days => CAST(:rd AS integer))))";
        MapSqlParameterSource p = new MapSqlParameterSource("j", jid).addValue("t", tenant)
                .addValue("rd", rd, Types.INTEGER).addValue("cap", ENROLL_CAP);

        switch (trigger) {
            case "OPTED_IN" -> {
                p.addValue("act", Timestamp.from(activated));
                return named.update(insert + " AND p.email_opted_in_at >= :act" + guard
                        + " ORDER BY p.email_opted_in_at LIMIT :cap ON CONFLICT DO NOTHING", p);
            }
            case "OPT_IN_ANNIVERSARY" -> {
                return named.update(insert + " AND p.email_opted_in_at < now() - interval '300 days'"
                        + " AND to_char(p.email_opted_in_at AT TIME ZONE 'Africa/Johannesburg', 'MM-DD') = to_char(now() AT TIME ZONE 'Africa/Johannesburg', 'MM-DD')"
                        + guard + " ORDER BY p.email_opted_in_at LIMIT :cap ON CONFLICT DO NOTHING", p);
            }
            case "SEGMENT_ENTRY" -> {
                List<UUID> ids = segmentService.resolveOptedInAudience(TenantId.of(tenant), segmentId);
                int total = 0;
                for (int i = 0; i < ids.size() && total < ENROLL_CAP; i += 1000) {
                    MapSqlParameterSource q = new MapSqlParameterSource(); q.addValues(p.getValues());
                    q.addValue("ids", ids.subList(i, Math.min(i + 1000, ids.size()))).addValue("cap", ENROLL_CAP - total);
                    total += named.update(insert + " AND p.id IN (:ids)" + guard + " LIMIT :cap ON CONFLICT DO NOTHING", q);
                }
                return total;
            }
            default -> { return 0; }
        }
    }

    // ── Processing ───────────────────────────────────────────────────────────

    @Scheduled(cron = "30 */2 * * * *", zone = "Africa/Johannesburg")
    public void processTick() {
        // Anyone who opted out or was suppressed leaves every journey straight away.
        int swept = named.update("UPDATE mkt_journey_enrollments e SET status = 'EXITED', exit_reason = 'Opted out or suppressed', "
                + "completed_at = now(), locked_at = NULL WHERE e.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM mkt_contact_preferences p "
                + "WHERE p.id = e.preference_id AND (p.email_opted_in = false OR " + MarketingAudienceService.SUPPRESSED_SQL + "))", Map.of());
        if (swept > 0) log.info("Journey sweep: {} enrolment(s) exited (opted out / suppressed)", swept);

        List<UUID> claimed = named.queryForList("""
            UPDATE mkt_journey_enrollments SET locked_at = now()
            WHERE id IN (SELECT e.id FROM mkt_journey_enrollments e JOIN mkt_journeys j ON j.id = e.journey_id
                         WHERE e.status = 'ACTIVE' AND j.status = 'ACTIVE' AND e.next_run_at <= now()
                           AND (e.locked_at IS NULL OR e.locked_at < now() - interval '10 minutes')
                         ORDER BY e.next_run_at LIMIT :cap FOR UPDATE OF e SKIP LOCKED)
            RETURNING id
            """, new MapSqlParameterSource("cap", PROCESS_CAP), UUID.class);
        if (claimed.isEmpty()) return;

        Map<UUID, String> tenantNames = new HashMap<>();
        int ok = 0, failed = 0;
        for (UUID id : claimed) {
            try {
                tx.executeWithoutResult(s -> processOne(id, tenantNames));
                ok++;
            } catch (Exception e) {
                failed++;
                log.error("Journey enrolment={} failed: {}", id, e.getMessage(), e);
                recordFailure(id, e);
            }
        }
        log.info("Journey processing: {} ok, {} failed", ok, failed);
    }

    private record Enr(UUID id, UUID tenant, UUID journeyId, UUID prefId, int step, String stepsJson, int gap, int ws, int we, boolean sw) {}

    private void processOne(UUID enrollmentId, Map<UUID, String> tenantNames) {
        Enr e = named.queryForObject("""
            SELECT e.id, e.tenant_id, e.journey_id, e.preference_id, e.current_step, CAST(j.steps AS text) AS steps,
                   j.min_gap_hours, j.window_start_hour, j.window_end_hour, j.skip_weekends
            FROM mkt_journey_enrollments e JOIN mkt_journeys j ON j.id = e.journey_id
            WHERE e.id = :id AND e.status = 'ACTIVE'
            """, new MapSqlParameterSource("id", enrollmentId),
                (rs, i) -> new Enr((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4),
                        rs.getInt(5), rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getBoolean(10)));
        if (e == null) return;

        List<StepDto> steps;
        try { steps = om.readValue(e.stepsJson(), new TypeReference<List<StepDto>>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Journey steps unreadable", ex); }

        TenantId tid = TenantId.of(e.tenant());
        Instant now = Instant.now(), next = now;
        int idx = e.step();
        String finalStatus = null, reason = null;

        for (int guard = 0; guard < MAX_STEPS_PER_RUN; guard++) {
            if (idx >= steps.size()) { finalStatus = "COMPLETED"; break; }
            StepDto s = steps.get(idx);

            if ("WAIT".equals(s.type())) {
                Duration d = "DAYS".equals(s.unit()) ? Duration.ofDays(s.amount()) : Duration.ofHours(s.amount());
                next = now.plus(d);
                addLog(e, idx, "WAIT", "WAITING", null, "Waiting " + s.amount() + " " + s.unit().toLowerCase());
                idx++;
                break;
            }

            if ("EXIT_IF".equals(s.type())) {
                if (engaged(e.id(), s.condition())) {
                    addLog(e, idx, "EXIT_IF", "GOAL_REACHED", null, "Contact " + s.condition().toLowerCase() + " an earlier email");
                    finalStatus = "COMPLETED"; reason = "Goal reached: " + s.condition().toLowerCase();
                    idx++;
                    break;
                }
                addLog(e, idx, "EXIT_IF", "GOAL_CHECKED", null, "Not " + s.condition().toLowerCase() + " yet; continuing");
                idx++;
                continue;
            }

            // EMAIL
            MktContactPreference pref = prefRepo.findById(e.prefId()).orElse(null);
            if (pref == null || !e.tenant().equals(pref.getTenantId().getValue()) || !pref.isEmailOptedIn()
                    || consentService.isSuppressed(tid, pref.getEmail())) {
                addLog(e, idx, "EMAIL", "CONSENT_EXIT", null, "Opted out or suppressed");
                finalStatus = "EXITED"; reason = "Opted out or suppressed";
                break;
            }
            Instant allowed = nextAllowed(now, e.ws(), e.we(), e.sw());
            if (allowed.isAfter(now)) { next = allowed; break; }

            Instant last = lastSent(e.tenant(), e.prefId());
            if (last != null && last.plus(Duration.ofHours(e.gap())).isAfter(now)) {
                next = nextAllowed(last.plus(Duration.ofHours(e.gap())), e.ws(), e.we(), e.sw());
                break;
            }

            MktCampaign c = s.campaignId() == null ? null : campaignRepo.findByIdAndTenantId(s.campaignId(), tid).orElse(null);
            if (c == null) throw new IllegalStateException("Step campaign missing for step " + (idx + 1));
            if (Set.of("PAUSED", "CANCELLED", "FAILED").contains(c.getStatus())) { next = now.plus(Duration.ofMinutes(30)); break; }
            if ("SENT".equals(c.getStatus())) {   // delivery engine marked the idle step campaign complete; reopen it
                named.update("UPDATE mkt_campaigns SET status = 'SENDING', sent_at = NULL, updated_at = now() WHERE id = :id AND status = 'SENT'",
                        new MapSqlParameterSource("id", c.getId()));
            }

            String company = tenantNames.computeIfAbsent(e.tenant(), this::tenantName);
            MktCampaignContact cc = MktCampaignContact.create(c.getId(), e.tenant(), pref.getEmail(), pref.getName(), pref.getId());
            ccRepo.save(cc);
            queueRepo.save(MktSendQueue.create(c.getId(), cc.getId(), e.tenant(), pref.getEmail(), pref.getName(),
                    marketing.renderSubject(c, pref, company), marketing.renderBody(c, pref, company, cc.getId()), null));
            named.update("UPDATE mkt_campaigns SET recipient_count = recipient_count + 1, updated_at = now() WHERE id = :id",
                    new MapSqlParameterSource("id", c.getId()));
            addLog(e, idx, "EMAIL", "SENT", cc.getId(), "Queued \"" + trunc(s.name(), 80) + "\"");
            idx++;
            next = now;
        }

        if (finalStatus == null && idx >= steps.size()) finalStatus = "COMPLETED";
        MapSqlParameterSource p = new MapSqlParameterSource("id", e.id()).addValue("idx", idx)
                .addValue("next", Timestamp.from(next)).addValue("reason", reason);
        if (finalStatus == null) {
            named.update("UPDATE mkt_journey_enrollments SET current_step = :idx, next_run_at = :next, locked_at = NULL, error_count = 0, last_error = NULL WHERE id = :id", p);
        } else {
            p.addValue("st", finalStatus);
            named.update("UPDATE mkt_journey_enrollments SET status = :st, current_step = :idx, exit_reason = :reason, completed_at = now(), locked_at = NULL, error_count = 0, last_error = NULL WHERE id = :id", p);
        }
    }

    private void recordFailure(UUID id, Exception ex) {
        String msg = trunc(String.valueOf(ex.getMessage()), 500);
        named.update("""
            UPDATE mkt_journey_enrollments SET error_count = error_count + 1, last_error = :m, locked_at = NULL,
                next_run_at = now() + interval '15 minutes',
                status = CASE WHEN error_count + 1 >= :max THEN 'EXITED' ELSE status END,
                exit_reason = CASE WHEN error_count + 1 >= :max THEN 'Stopped after repeated errors' ELSE exit_reason END,
                completed_at = CASE WHEN error_count + 1 >= :max THEN now() ELSE completed_at END
            WHERE id = :id
            """, new MapSqlParameterSource("m", msg).addValue("max", MAX_ERRORS).addValue("id", id));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Earliest instant at or after {@code from} inside the SAST send window (and not on a skipped weekend). */
    static Instant nextAllowed(Instant from, int startHour, int endHour, boolean skipWeekends) {
        ZonedDateTime z = from.atZone(SAST);
        for (int i = 0; i < 10; i++) {
            boolean weekend = skipWeekends && (z.getDayOfWeek() == DayOfWeek.SATURDAY || z.getDayOfWeek() == DayOfWeek.SUNDAY);
            if (!weekend) {
                if (z.getHour() < startHour) return z.withHour(startHour).withMinute(0).withSecond(0).withNano(0).toInstant();
                if (z.getHour() < endHour) return z.toInstant();
            }
            z = z.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        return from;
    }

    private boolean engaged(UUID enrollmentId, String condition) {
        String col = "OPENED".equals(condition) ? "opened_at" : "clicked_at";
        Boolean b = named.queryForObject("SELECT EXISTS (SELECT 1 FROM mkt_journey_step_log l JOIN mkt_campaign_contacts cc ON cc.id = l.campaign_contact_id "
                        + "WHERE l.enrollment_id = :e AND l.outcome = 'SENT' AND cc." + col + " IS NOT NULL)",
                new MapSqlParameterSource("e", enrollmentId), Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    private Instant lastSent(UUID tenant, UUID prefId) {
        Timestamp t = named.queryForObject("SELECT MAX(created_at) FROM mkt_journey_step_log WHERE tenant_id = :t AND preference_id = :p AND outcome = 'SENT'",
                new MapSqlParameterSource("t", tenant).addValue("p", prefId), Timestamp.class);
        return t == null ? null : t.toInstant();
    }

    private void addLog(Enr e, int step, String type, String outcome, UUID ccId, String detail) {
        named.update("INSERT INTO mkt_journey_step_log (id, tenant_id, journey_id, enrollment_id, preference_id, step_index, step_type, outcome, campaign_contact_id, detail) "
                        + "VALUES (:id, :t, :j, :e, :p, :s, :ty, :o, :cc, :d)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("t", e.tenant()).addValue("j", e.journeyId()).addValue("e", e.id())
                        .addValue("p", e.prefId()).addValue("s", step).addValue("ty", type).addValue("o", outcome)
                        .addValue("cc", ccId).addValue("d", trunc(detail, 300)));
    }

    private String tenantName(UUID tenant) {
        try { return named.queryForObject("SELECT name FROM tenants WHERE id = :t", new MapSqlParameterSource("t", tenant), String.class); }
        catch (Exception ex) { return "HandyFlow"; }
    }

    private static String trunc(String s, int n) { return s == null ? "" : s.length() > n ? s.substring(0, n) : s; }
}