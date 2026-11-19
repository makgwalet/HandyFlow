package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.marketing.dto.AudiencePreviewResponse;
import za.co.handyflow.platform.marketing.dto.AudiencePreviewResponse.SampleContact;
import za.co.handyflow.platform.marketing.dto.ContactPreferenceResponse;
import za.co.handyflow.platform.marketing.dto.SegmentDefinition;
import za.co.handyflow.platform.marketing.dto.SegmentDefinition.Condition;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Audience engine: compiles segment rules into parameterised SQL, previews audiences,
 * resolves them at launch, and powers server-side contact search.
 *
 * SAFETY
 *  - Field names and operators come from a fixed whitelist; they are never taken from input as SQL.
 *  - Every value is a bound parameter. Nothing user-supplied is concatenated into SQL.
 *  - Every query is scoped by tenant_id.
 *  - An audience only ever RESOLVES opted-in contacts; the suppression check is applied by the caller
 *    (launch) and again at send time. Previews report what suppression would remove.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingAudienceService {

    private final NamedParameterJdbcTemplate named;

    /** A WHERE clause (aliasing mkt_contact_preferences as p) plus its bound parameters. */
    public record Scope(String where, MapSqlParameterSource params) {}

    private static final int MAX_CONDITIONS = 20;
    private static final int MAX_VALUES     = 100;
    private static final int MAX_EXPORT     = 50_000;
    private static final int IN_CHUNK       = 1_000;

    static final String SUPPRESSED_SQL =
            "EXISTS (SELECT 1 FROM mkt_suppression_entries s WHERE s.tenant_id = p.tenant_id "
                    + "AND s.channel = 'EMAIL' AND s.email = p.email AND s.lifted_at IS NULL)";

    private static final Map<String, Set<String>> FIELD_OPS = Map.of(
            "OPT_IN_SOURCE", Set.of("IN", "NOT_IN"),
            "ENTITY_TYPE",   Set.of("IN", "NOT_IN"),
            "EMAIL_DOMAIN",  Set.of("IN", "NOT_IN"),
            "OPTED_IN_DATE", Set.of("ON_OR_AFTER", "BEFORE"),
            "CLICKED",       Set.of("WITHIN_DAYS", "NOT_WITHIN_DAYS"),
            "OPENED",        Set.of("WITHIN_DAYS", "NOT_WITHIN_DAYS"),
            "EMAILED",       Set.of("WITHIN_DAYS", "NOT_WITHIN_DAYS"));

    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}$");

    // ── Rule compilation ──────────────────────────────────────────────────────

    /** Validates the definition and returns it with normalised case/whitespace, ready to store. */
    public SegmentDefinition normalise(SegmentDefinition def) {
        dynamicScope(UUID.randomUUID(), def);   // throws HandyFlowException(400) if invalid
        List<Condition> out = new ArrayList<>();
        for (int i = 0; i < def.conditions().size(); i++) {
            Condition c = def.conditions().get(i);
            out.add(new Condition(up(c.field()), up(c.op()), cleanValues(c, i)));
        }
        return new SegmentDefinition("ANY".equalsIgnoreCase(def.match()) ? "ANY" : "ALL", out);
    }

    public Scope dynamicScope(UUID tenantId, SegmentDefinition def) {
        if (def == null || def.conditions() == null || def.conditions().isEmpty()) {
            throw bad("Add at least one rule. To email everyone who is opted in, use the 'All opted-in contacts' audience instead.");
        }
        if (def.conditions().size() > MAX_CONDITIONS) throw bad("A segment can have at most " + MAX_CONDITIONS + " rules.");

        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);
        List<String> parts = new ArrayList<>();

        for (int i = 0; i < def.conditions().size(); i++) {
            Condition c = def.conditions().get(i);
            String field = up(c.field());
            String op    = up(c.op());
            Set<String> allowedOps = FIELD_OPS.get(field);
            if (allowedOps == null)          throw bad("Rule " + (i + 1) + ": unknown field '" + c.field() + "'.");
            if (!allowedOps.contains(op))    throw bad("Rule " + (i + 1) + ": '" + c.op() + "' is not valid for " + field + ".");

            List<String> v = cleanValues(c, i);
            String key = "c" + i;
            boolean in = op.equals("IN");

            switch (field) {
                case "OPT_IN_SOURCE" -> {
                    params.addValue(key, v.stream().map(String::toUpperCase).toList());
                    parts.add(in ? "UPPER(p.opt_in_source) IN (:" + key + ")"
                            : "(p.opt_in_source IS NULL OR UPPER(p.opt_in_source) NOT IN (:" + key + "))");
                }
                case "ENTITY_TYPE" -> {
                    List<String> types = v.stream().map(String::toUpperCase).toList();
                    if (!Set.of("CUSTOMER", "SUBSCRIBER").containsAll(types)) {
                        throw bad("Rule " + (i + 1) + ": contact type must be CUSTOMER or SUBSCRIBER.");
                    }
                    params.addValue(key, types);
                    parts.add("p.entity_type " + (in ? "IN" : "NOT IN") + " (:" + key + ")");
                }
                case "EMAIL_DOMAIN" -> {
                    List<String> domains = v.stream().map(s -> s.toLowerCase().replaceFirst("^@", "")).toList();
                    for (String d : domains) {
                        if (!DOMAIN.matcher(d).matches()) throw bad("Rule " + (i + 1) + ": '" + d + "' is not a valid domain (e.g. example.co.za).");
                    }
                    params.addValue(key, domains);
                    parts.add("LOWER(SPLIT_PART(p.email, '@', 2)) " + (in ? "IN" : "NOT IN") + " (:" + key + ")");
                }
                case "OPTED_IN_DATE" -> {
                    LocalDate d;
                    try { d = LocalDate.parse(v.get(0)); }
                    catch (DateTimeParseException e) { throw bad("Rule " + (i + 1) + ": date must be YYYY-MM-DD."); }
                    params.addValue(key, d.toString());
                    String cmp = op.equals("ON_OR_AFTER") ? ">=" : "<";
                    parts.add("(p.email_opted_in_at IS NOT NULL AND "
                            + "CAST(p.email_opted_in_at AT TIME ZONE 'Africa/Johannesburg' AS date) " + cmp + " CAST(:" + key + " AS date))");
                }
                default -> { // CLICKED | OPENED | EMAILED
                    int days;
                    try { days = Integer.parseInt(v.get(0)); }
                    catch (NumberFormatException e) { throw bad("Rule " + (i + 1) + ": days must be a whole number."); }
                    if (days < 1 || days > 730) throw bad("Rule " + (i + 1) + ": days must be between 1 and 730.");
                    String column = switch (field) {
                        case "CLICKED" -> "clicked_at";
                        case "OPENED"  -> "opened_at";
                        default        -> "sent_at";
                    };
                    params.addValue(key, days);
                    parts.add((op.equals("WITHIN_DAYS") ? "EXISTS" : "NOT EXISTS")
                            + " (SELECT 1 FROM mkt_campaign_contacts cc WHERE cc.preference_id = p.id AND cc.tenant_id = p.tenant_id "
                            + "AND cc." + column + " >= now() - make_interval(days => :" + key + "))");
                }
            }
        }
        String joiner = "ANY".equalsIgnoreCase(def.match()) ? " OR " : " AND ";
        return new Scope("p.tenant_id = :tenantId AND (" + String.join(joiner, parts) + ")", params);
    }

    public Scope staticScope(UUID tenantId, UUID segmentId) {
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId).addValue("segmentId", segmentId);
        return new Scope("p.tenant_id = :tenantId AND p.id IN (SELECT m.preference_id FROM mkt_segment_members m "
                + "WHERE m.segment_id = :segmentId AND m.tenant_id = :tenantId)", params);
    }

    // ── Preview + resolution ──────────────────────────────────────────────────

    public AudiencePreviewResponse preview(Scope s) {
        Map<String, Object> row = named.queryForMap(
                "SELECT COUNT(*) AS matched, "
                        + "COUNT(*) FILTER (WHERE p.email_opted_in) AS opted_in, "
                        + "COUNT(*) FILTER (WHERE p.email_opted_in AND " + SUPPRESSED_SQL + ") AS suppressed "
                        + "FROM mkt_contact_preferences p WHERE " + s.where(), s.params());
        long matched = num(row.get("matched")), optedIn = num(row.get("opted_in")), suppressed = num(row.get("suppressed"));

        List<SampleContact> sample = named.query(
                "SELECT p.name, p.email FROM mkt_contact_preferences p WHERE " + s.where()
                        + " AND p.email_opted_in = true AND NOT " + SUPPRESSED_SQL
                        + " ORDER BY p.created_at DESC LIMIT 8",
                s.params(), (rs, i) -> new SampleContact(rs.getString("name"), rs.getString("email")));

        return new AudiencePreviewResponse(matched, matched - optedIn, suppressed, optedIn - suppressed, sample);
    }

    /** Opted-in contacts in scope. Suppression is applied by the caller. */
    public List<UUID> resolveOptedInIds(Scope s) {
        return named.queryForList("SELECT p.id FROM mkt_contact_preferences p WHERE " + s.where()
                + " AND p.email_opted_in = true ORDER BY p.created_at, p.id", s.params(), UUID.class);
    }

    // ── Server-side contact search ────────────────────────────────────────────

    public Page<ContactPreferenceResponse> searchContacts(UUID tenantId, String search, String status,
                                                          String source, Pageable pageable) {
        Scope s = contactFilter(tenantId, search, status, source);
        int size = Math.max(1, Math.min(pageable.getPageSize(), 200));
        int page = Math.max(0, pageable.getPageNumber());

        Long total = named.queryForObject("SELECT COUNT(*) FROM mkt_contact_preferences p WHERE " + s.where(),
                s.params(), Long.class);

        MapSqlParameterSource params = copy(s.params()).addValue("limit", size).addValue("offset", (long) page * size);
        List<ContactPreferenceResponse> rows = named.query(
                "SELECT p.id, p.email, p.name, p.entity_type, p.entity_id, p.email_opted_in, p.email_opted_in_at, "
                        + "p.email_opted_out_at, p.opt_in_source, p.created_at, " + SUPPRESSED_SQL + " AS suppressed "
                        + "FROM mkt_contact_preferences p WHERE " + s.where()
                        + " ORDER BY p.created_at DESC, p.id LIMIT :limit OFFSET :offset",
                params, CONTACT_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    /** Protected export (MARKETING_ADMIN). Capped at {@value #MAX_EXPORT} rows; formula-injection safe. */
    public String exportContactsCsv(UUID tenantId, String search, String status, String source) {
        Scope s = contactFilter(tenantId, search, status, source);
        MapSqlParameterSource params = copy(s.params()).addValue("limit", MAX_EXPORT);
        List<ContactPreferenceResponse> rows = named.query(
                "SELECT p.id, p.email, p.name, p.entity_type, p.entity_id, p.email_opted_in, p.email_opted_in_at, "
                        + "p.email_opted_out_at, p.opt_in_source, p.created_at, " + SUPPRESSED_SQL + " AS suppressed "
                        + "FROM mkt_contact_preferences p WHERE " + s.where()
                        + " ORDER BY p.created_at DESC, p.id LIMIT :limit", params, CONTACT_MAPPER);

        StringBuilder sb = new StringBuilder("Name,Email,Status,Opted in,Source,Opted in at,Opted out at,Type,Created\r\n");
        for (ContactPreferenceResponse c : rows) {
            String st = c.suppressed() ? "SUPPRESSED" : c.emailOptedIn() ? "OPTED_IN" : "NOT_OPTED_IN";
            sb.append(String.join(",",
                    cell(c.name()), cell(c.email()), cell(st), cell(String.valueOf(c.emailOptedIn())),
                    cell(c.optInSource()), cell(iso(c.emailOptedInAt())), cell(iso(c.emailOptedOutAt())),
                    cell(c.entityType()), cell(iso(c.createdAt())))).append("\r\n");
        }
        return sb.toString();
    }

    public long countConsentExceptions(UUID tenantId) {
        Long n = named.queryForObject("SELECT COUNT(*) FROM mkt_contact_preferences p WHERE p.tenant_id = :t "
                        + "AND p.email_opted_in = true AND " + SUPPRESSED_SQL,
                new MapSqlParameterSource("t", tenantId), Long.class);
        return n == null ? 0 : n;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Scope contactFilter(UUID tenantId, String search, String status, String source) {
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);
        StringBuilder where = new StringBuilder("p.tenant_id = :tenantId");

        if (search != null && !search.isBlank()) {
            String escaped = search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            params.addValue("q", "%" + escaped + "%");
            where.append(" AND (LOWER(p.email) LIKE :q ESCAPE '\\' OR LOWER(COALESCE(p.name, '')) LIKE :q ESCAPE '\\')");
        }
        if (source != null && !source.isBlank()) {
            params.addValue("source", source.trim().toUpperCase());
            where.append(" AND UPPER(p.opt_in_source) = :source");
        }
        String st = status == null ? "ALL" : status.trim().toUpperCase();
        switch (st) {
            case "ALL" -> { }
            case "SUPPRESSED"    -> where.append(" AND ").append(SUPPRESSED_SQL);
            case "OPTED_IN"      -> where.append(" AND p.email_opted_in = true AND NOT ").append(SUPPRESSED_SQL);
            case "NOT_OPTED_IN"  -> where.append(" AND p.email_opted_in = false AND NOT ").append(SUPPRESSED_SQL);
            default -> throw bad("Unknown status filter '" + status + "'.");
        }
        return new Scope(where.toString(), params);
    }

    private static final RowMapper<ContactPreferenceResponse> CONTACT_MAPPER = (rs, i) -> new ContactPreferenceResponse(
            (UUID) rs.getObject("id"), rs.getString("email"), rs.getString("name"),
            rs.getString("entity_type"), (UUID) rs.getObject("entity_id"),
            rs.getBoolean("email_opted_in"),
            ts(rs.getTimestamp("email_opted_in_at")), ts(rs.getTimestamp("email_opted_out_at")),
            rs.getString("opt_in_source"), ts(rs.getTimestamp("created_at")),
            rs.getBoolean("suppressed"));

    private List<String> cleanValues(Condition c, int idx) {
        if (c.values() == null) throw bad("Rule " + (idx + 1) + " needs a value.");
        List<String> out = c.values().stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().toList();
        if (out.isEmpty())                               throw bad("Rule " + (idx + 1) + " needs a value.");
        if (out.size() > MAX_VALUES)                     throw bad("Rule " + (idx + 1) + " has too many values (max " + MAX_VALUES + ").");
        if (out.stream().anyMatch(s -> s.length() > 255)) throw bad("Rule " + (idx + 1) + " has a value that is too long.");
        return out;
    }

    private static MapSqlParameterSource copy(MapSqlParameterSource src) {
        MapSqlParameterSource c = new MapSqlParameterSource();
        c.addValues(src.getValues());
        return c;
    }

    private static HandyFlowException bad(String msg) {
        return new HandyFlowException(msg, HttpStatus.BAD_REQUEST, "SEGMENT_INVALID");
    }
    private static String up(String s) { return s == null ? "" : s.trim().toUpperCase(); }
    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
    private static java.time.Instant ts(Timestamp t) { return t == null ? null : t.toInstant(); }
    private static String iso(java.time.Instant i) { return i == null ? "" : i.toString(); }

    /** CSV cell: always quoted, and spreadsheet-formula prefixes neutralised. */
    private static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}