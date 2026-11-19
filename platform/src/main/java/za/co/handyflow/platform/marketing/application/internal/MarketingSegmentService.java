package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.application.internal.MarketingAudienceService.Scope;
import za.co.handyflow.platform.marketing.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Segment CRUD, static-list membership, and audience resolution for launch. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingSegmentService {

    private static final int MAX_MEMBERS_PER_REQUEST = 5_000;
    private static final int IN_CHUNK = 1_000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final NamedParameterJdbcTemplate named;
    private final MarketingAudienceService   audience;
    private final ObjectMapper               objectMapper;

    private static final String SELECT = """
        SELECT s.id, s.name, s.description, s.segment_type, CAST(s.definition AS text) AS definition_json,
               s.last_count, s.last_counted_at, s.created_at, s.updated_at,
               (SELECT COUNT(*) FROM mkt_campaigns c
                 WHERE c.segment_id = s.id AND c.deleted_at IS NULL
                   AND c.status IN ('DRAFT','SCHEDULED','SENDING','PAUSED')) AS used_by
        FROM mkt_segments s
        """;

    private record Row(UUID id, String name, String type, String definitionJson) {}

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SegmentResponse> list(TenantId tenantId) {
        return named.query(SELECT + " WHERE s.tenant_id = :t AND s.deleted_at IS NULL ORDER BY LOWER(s.name)",
                new MapSqlParameterSource("t", tenantId.getValue()), (rs, i) -> map(rs));
    }

    @Transactional(readOnly = true)
    public SegmentResponse get(TenantId tenantId, UUID id) {
        List<SegmentResponse> rows = named.query(SELECT + " WHERE s.id = :id AND s.tenant_id = :t AND s.deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue()), (rs, i) -> map(rs));
        if (rows.isEmpty()) throw new ResourceNotFoundException("Segment", id.toString());
        return rows.get(0);
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public SegmentResponse create(TenantId tenantId, UUID user, SegmentRequest req) {
        String type = req.type().toUpperCase();
        String defJson = null;
        if ("DYNAMIC".equals(type)) {
            defJson = write(audience.normalise(req.definition()));
        }
        UUID id = UUID.randomUUID();
        try {
            named.update("""
                INSERT INTO mkt_segments (id, tenant_id, name, description, segment_type, definition, created_by, updated_by)
                VALUES (:id, :t, :name, CAST(:desc AS text), :type, CAST(:def AS jsonb), CAST(:user AS uuid), CAST(:user AS uuid))
                """, new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue())
                    .addValue("name", req.name().trim()).addValue("desc", blankToNull(req.description()))
                    .addValue("type", type).addValue("def", defJson).addValue("user", user == null ? null : user.toString()));
        } catch (DuplicateKeyException e) {
            throw nameTaken();
        }
        refreshCount(tenantId, id);
        return get(tenantId, id);
    }

    @Transactional
    public SegmentResponse update(TenantId tenantId, UUID user, UUID id, SegmentRequest req) {
        Row existing = load(tenantId, id);
        if (!existing.type().equalsIgnoreCase(req.type())) {
            throw new HandyFlowException("A segment's type cannot be changed. Create a new segment instead.",
                    HttpStatus.CONFLICT, "SEGMENT_TYPE_IMMUTABLE");
        }
        String defJson = "DYNAMIC".equals(existing.type()) ? write(audience.normalise(req.definition())) : null;
        try {
            named.update("""
                UPDATE mkt_segments
                SET name = :name, description = CAST(:desc AS text), definition = CAST(:def AS jsonb),
                    updated_by = CAST(:user AS uuid), updated_at = now()
                WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL
                """, new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue())
                    .addValue("name", req.name().trim()).addValue("desc", blankToNull(req.description()))
                    .addValue("def", defJson).addValue("user", user == null ? null : user.toString()));
        } catch (DuplicateKeyException e) {
            throw nameTaken();
        }
        refreshCount(tenantId, id);
        return get(tenantId, id);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        SegmentResponse seg = get(tenantId, id);
        if (seg.usedByActiveCampaigns() > 0) {
            throw new HandyFlowException("This segment is used by " + seg.usedByActiveCampaigns()
                    + " campaign(s) that have not finished (draft, scheduled, sending or paused). "
                    + "Cancel or finish those campaigns first.", HttpStatus.CONFLICT, "SEGMENT_IN_USE");
        }
        named.update("UPDATE mkt_segments SET deleted_at = now(), updated_at = now() WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue()));
    }

    // ── Preview / counts ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AudiencePreviewResponse previewDefinition(TenantId tenantId, SegmentDefinition def) {
        return audience.preview(audience.dynamicScope(tenantId.getValue(), def));
    }

    @Transactional
    public AudiencePreviewResponse previewSaved(TenantId tenantId, UUID id) {
        AudiencePreviewResponse p = audience.preview(scopeOf(tenantId, load(tenantId, id)));
        storeCount(tenantId, id, p.eligible());
        return p;
    }

    @Transactional
    public SegmentResponse refreshCount(TenantId tenantId, UUID id) {
        AudiencePreviewResponse p = audience.preview(scopeOf(tenantId, load(tenantId, id)));
        storeCount(tenantId, id, p.eligible());
        return get(tenantId, id);
    }

    // ── Launch integration ────────────────────────────────────────────────────

    /** Returns the segment's name, or throws if it was deleted — used to validate a campaign's audience. */
    @Transactional(readOnly = true)
    public String requireActiveName(TenantId tenantId, UUID segmentId) {
        if (segmentId == null) {
            throw new HandyFlowException("Choose a segment for this audience.", HttpStatus.BAD_REQUEST, "SEGMENT_REQUIRED");
        }
        try {
            return load(tenantId, segmentId).name();
        } catch (ResourceNotFoundException e) {
            throw new HandyFlowException("The segment for this campaign no longer exists.",
                    HttpStatus.BAD_REQUEST, "SEGMENT_UNAVAILABLE");
        }
    }

    /** Opted-in contacts in the segment RIGHT NOW (dynamic rules re-evaluated). Suppression is applied by the caller. */
    @Transactional(readOnly = true)
    public List<UUID> resolveOptedInAudience(TenantId tenantId, UUID segmentId) {
        return audience.resolveOptedInIds(scopeOf(tenantId, load(tenantId, segmentId)));
    }

    // ── Static list membership ────────────────────────────────────────────────

    @Transactional
    public AddMembersResult addMembers(TenantId tenantId, UUID user, UUID segmentId, AddSegmentMembersRequest req) {
        Row seg = load(tenantId, segmentId);
        if (!"STATIC".equals(seg.type())) {
            throw new HandyFlowException("Members can only be added to a static list.", HttpStatus.CONFLICT, "SEGMENT_NOT_STATIC");
        }

        Set<String> emails = new LinkedHashSet<>();
        if (req.emails() != null) {
            for (String e : req.emails()) {
                if (e == null) continue;
                String n = e.toLowerCase().trim();
                if (EMAIL.matcher(n).matches()) emails.add(n);
            }
        }
        Set<UUID> ids = req.preferenceIds() == null ? new LinkedHashSet<>()
                : req.preferenceIds().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        int requested = emails.size() + ids.size();
        if (requested == 0) throw new HandyFlowException("Provide at least one valid email address.", HttpStatus.BAD_REQUEST, "NO_MEMBERS");
        if (requested > MAX_MEMBERS_PER_REQUEST) {
            throw new HandyFlowException("Add at most " + MAX_MEMBERS_PER_REQUEST + " members at a time.", HttpStatus.BAD_REQUEST, "TOO_MANY_MEMBERS");
        }

        // Only EXISTING contacts of this tenant can join a list — no consent exists for anyone else.
        Set<UUID> found = new LinkedHashSet<>();
        for (List<String> chunk : chunks(new ArrayList<>(emails))) {
            found.addAll(named.queryForList("SELECT id FROM mkt_contact_preferences WHERE tenant_id = :t AND email IN (:e)",
                    new MapSqlParameterSource("t", tenantId.getValue()).addValue("e", chunk), UUID.class));
        }
        for (List<UUID> chunk : chunks(new ArrayList<>(ids))) {
            found.addAll(named.queryForList("SELECT id FROM mkt_contact_preferences WHERE tenant_id = :t AND id IN (:i)",
                    new MapSqlParameterSource("t", tenantId.getValue()).addValue("i", chunk), UUID.class));
        }

        long before = memberCount(segmentId);
        for (UUID prefId : found) {
            named.update("""
                INSERT INTO mkt_segment_members (segment_id, preference_id, tenant_id, added_by)
                VALUES (:s, :p, :t, CAST(:u AS uuid)) ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("s", segmentId).addValue("p", prefId)
                    .addValue("t", tenantId.getValue()).addValue("u", user == null ? null : user.toString()));
        }
        int added = (int) (memberCount(segmentId) - before);
        refreshCount(tenantId, segmentId);

        // Note: two addresses can resolve to the same contact, so "notFound" is a floor-based estimate.
        int notFound = Math.max(0, requested - found.size());
        return new AddMembersResult(added, Math.max(0, found.size() - added), notFound);
    }

    @Transactional
    public void removeMember(TenantId tenantId, UUID segmentId, UUID preferenceId) {
        load(tenantId, segmentId);
        named.update("DELETE FROM mkt_segment_members WHERE segment_id = :s AND preference_id = :p AND tenant_id = :t",
                new MapSqlParameterSource("s", segmentId).addValue("p", preferenceId).addValue("t", tenantId.getValue()));
        refreshCount(tenantId, segmentId);
    }

    @Transactional(readOnly = true)
    public Page<SegmentMemberResponse> listMembers(TenantId tenantId, UUID segmentId, Pageable pageable) {
        Row seg = load(tenantId, segmentId);
        if (!"STATIC".equals(seg.type())) {
            throw new HandyFlowException("Only static lists have a fixed member list. Use preview for dynamic segments.",
                    HttpStatus.CONFLICT, "SEGMENT_NOT_STATIC");
        }
        int size = Math.max(1, Math.min(pageable.getPageSize(), 200));
        int page = Math.max(0, pageable.getPageNumber());
        MapSqlParameterSource p = new MapSqlParameterSource("s", segmentId).addValue("t", tenantId.getValue())
                .addValue("limit", size).addValue("offset", (long) page * size);

        Long total = named.queryForObject("SELECT COUNT(*) FROM mkt_segment_members WHERE segment_id = :s AND tenant_id = :t", p, Long.class);
        List<SegmentMemberResponse> rows = named.query(
                "SELECT p.id, p.email, p.name, p.email_opted_in, m.added_at, " + MarketingAudienceService.SUPPRESSED_SQL + " AS suppressed "
                        + "FROM mkt_segment_members m JOIN mkt_contact_preferences p ON p.id = m.preference_id "
                        + "WHERE m.segment_id = :s AND m.tenant_id = :t ORDER BY m.added_at DESC, p.email LIMIT :limit OFFSET :offset",
                p, (rs, i) -> new SegmentMemberResponse((UUID) rs.getObject("id"), rs.getString("email"), rs.getString("name"),
                        rs.getBoolean("email_opted_in"), rs.getBoolean("suppressed"), rs.getTimestamp("added_at").toInstant()));
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Row load(TenantId tenantId, UUID id) {
        List<Row> rows = named.query(
                "SELECT id, name, segment_type, CAST(definition AS text) AS definition_json FROM mkt_segments "
                        + "WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue()),
                (rs, i) -> new Row((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("segment_type"),
                        rs.getString("definition_json")));
        if (rows.isEmpty()) throw new ResourceNotFoundException("Segment", id.toString());
        return rows.get(0);
    }

    private Scope scopeOf(TenantId tenantId, Row row) {
        if ("STATIC".equals(row.type())) return audience.staticScope(tenantId.getValue(), row.id());
        try {
            return audience.dynamicScope(tenantId.getValue(), objectMapper.readValue(row.definitionJson(), SegmentDefinition.class));
        } catch (HandyFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new HandyFlowException("This segment's rules could not be read. Edit and save it again.",
                    HttpStatus.CONFLICT, "SEGMENT_CORRUPT");
        }
    }

    private void storeCount(TenantId tenantId, UUID id, long eligible) {
        named.update("UPDATE mkt_segments SET last_count = :n, last_counted_at = now() WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("n", (int) Math.min(eligible, Integer.MAX_VALUE)).addValue("id", id).addValue("t", tenantId.getValue()));
    }

    private long memberCount(UUID segmentId) {
        Long n = named.queryForObject("SELECT COUNT(*) FROM mkt_segment_members WHERE segment_id = :s",
                new MapSqlParameterSource("s", segmentId), Long.class);
        return n == null ? 0 : n;
    }

    private SegmentResponse map(ResultSet rs) throws SQLException {
        SegmentDefinition def = null;
        String json = rs.getString("definition_json");
        if (json != null) {
            try { def = objectMapper.readValue(json, SegmentDefinition.class); }
            catch (Exception e) { log.warn("Could not parse definition of segment {}: {}", rs.getObject("id"), e.getMessage()); }
        }
        Timestamp counted = rs.getTimestamp("last_counted_at");
        return new SegmentResponse((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("segment_type"), def,
                rs.getObject("last_count") == null ? null : rs.getInt("last_count"),
                counted == null ? null : counted.toInstant(),
                rs.getLong("used_by"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private String write(SegmentDefinition def) {
        try { return objectMapper.writeValueAsString(def); }
        catch (Exception e) { throw new IllegalStateException("Could not serialise segment definition", e); }
    }

    private static <T> List<List<T>> chunks(List<T> all) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += IN_CHUNK) out.add(all.subList(i, Math.min(i + IN_CHUNK, all.size())));
        return out;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static HandyFlowException nameTaken() {
        return new HandyFlowException("A segment with this name already exists.", HttpStatus.CONFLICT, "SEGMENT_NAME_EXISTS");
    }
}