// security/application/internal/CoverageService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Cover;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Post;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Result;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Array;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;

/** Coverage of the roster against what each site needs, for today and the days ahead. */
@Service("securityCoverageService")
@RequiredArgsConstructor
public class CoverageService {

    static final int MAX_POSTS = 500;

    public record GapView(Instant start, Instant end, int have, int need) {}
    public record Row(UUID siteId, String siteName, UUID postId, String postName, LocalDate day, Instant start, Instant end,
                      int needed, String state, List<GapView> gaps) {}
    public record Board(LocalDate from, int days, int postCount, int shortCount, int uncoveredCount, List<Row> rows) {}
    public record PostView(UUID id, UUID siteId, String name, List<Integer> weekdays, LocalTime start, LocalTime end, int minGuards) {}

    /** Optional so a service built by hand still works; without it South Africa's zone is used, never the server's. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.BusinessCalendar calendar;

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Board board(TenantId tenantId, UUID siteId, Integer daysAsked) { return board(tenantId, siteId, daysAsked, Instant.now(), null); }

    /** The board for a user limited to some sites. `allowedSites` null means the whole company. */
    @Transactional(readOnly = true)
    public Board board(TenantId tenantId, UUID siteId, Integer daysAsked, Set<UUID> allowedSites) { return board(tenantId, siteId, daysAsked, Instant.now(), allowedSites); }

    @Transactional(readOnly = true)
    Board board(TenantId tenantId, UUID siteId, Integer daysAsked, Instant now) { return board(tenantId, siteId, daysAsked, now, null); }

    @Transactional(readOnly = true)
    Board board(TenantId tenantId, UUID siteId, Integer daysAsked, Instant now, Set<UUID> allowedSites) {
        int days = CoverageRules.clampDays(daysAsked);
        java.time.ZoneId zone = za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId);
        LocalDate from = now.atZone(zone).toLocalDate();
        UUID tenant = tenantId.getValue();

        Map<UUID, String> siteNames = new HashMap<>();
        List<Post> posts = new ArrayList<>();
        String siteFilter = siteId == null ? "" : " AND p.site_id = ?";
        List<Object> args = new ArrayList<>(List.of(tenant));
        if (siteId != null) args.add(siteId);
        if (allowedSites != null) {
            // A user limited to some sites: only their posts, and nothing at all when they have none.
            if (allowedSites.isEmpty()) return new Board(from, days, 0, 0, 0, List.of());
            siteFilter += " AND p.site_id IN (" + String.join(",", java.util.Collections.nCopies(allowedSites.size(), "?")) + ")";
            args.addAll(allowedSites);
        }
        jdbc.query("""
                SELECT p.id, p.site_id, s.name AS site_name, p.name, p.weekdays, p.start_time, p.end_time, p.min_guards
                FROM security_site_posts p JOIN security_sites s ON s.id = p.site_id AND s.tenant_id = p.tenant_id
                WHERE p.tenant_id = ? AND p.active AND s.deleted_at IS NULL""" + siteFilter + " ORDER BY s.name, p.name LIMIT " + MAX_POSTS,
                rs -> {
                    UUID sid = (UUID) rs.getObject("site_id");
                    siteNames.put(sid, rs.getString("site_name"));
                    posts.add(new Post((UUID) rs.getObject("id"), sid, rs.getString("name"), weekdaysOf(rs.getArray("weekdays")),
                            rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class), rs.getInt("min_guards")));
                }, args.toArray());

        Instant windowStart = from.atStartOfDay(zone).toInstant();
        Instant windowEnd = from.plusDays(days + 1L).atStartOfDay(zone).toInstant();
        Map<UUID, List<Cover>> covers = new HashMap<>();
        if (!posts.isEmpty()) {
            List<Object> sargs = new ArrayList<>(List.of(tenant, utc(windowEnd), utc(windowStart)));
            String sf = "";
            if (siteId != null) { sf = " AND site_id = ?"; sargs.add(siteId); }
            jdbc.query("""
                    SELECT site_id, start_at, end_at FROM security_shifts
                    WHERE tenant_id = ? AND deleted_at IS NULL AND status IN ('SCHEDULED','ACTIVE','COMPLETED')
                      AND start_at < ? AND end_at > ?""" + sf,
                    rs -> {
                        covers.computeIfAbsent((UUID) rs.getObject("site_id"), k -> new ArrayList<>()).add(new Cover(
                                rs.getObject("start_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                                rs.getObject("end_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
                    }, sargs.toArray());
        }

        List<Row> rows = new ArrayList<>();
        Map<UUID, List<Post>> bySite = new LinkedHashMap<>();
        for (Post p : posts) bySite.computeIfAbsent(p.siteId(), k -> new ArrayList<>()).add(p);
        int shortC = 0, uncovered = 0;
        for (var e : bySite.entrySet()) {
            for (Result r : CoverageRules.evaluateAll(e.getValue(), from, days, covers.getOrDefault(e.getKey(), List.of()), zone)) {
                if (r.state() == CoverageRules.State.SHORT) shortC++;
                if (r.state() == CoverageRules.State.UNCOVERED) uncovered++;
                rows.add(new Row(e.getKey(), siteNames.get(e.getKey()), r.window().post().id(), r.window().post().name(), r.window().day(),
                        r.window().start(), r.window().end(), r.window().post().minGuards(), r.state().name(),
                        r.gaps().stream().map(g -> new GapView(g.start(), g.end(), g.have(), g.need())).toList()));
            }
        }
        rows.sort(Comparator.comparing(Row::start).thenComparing(Row::siteName).thenComparing(Row::postName));
        return new Board(from, days, posts.size(), shortC, uncovered, rows);
    }

    /**
     * Posts that still need action today, for the dashboard: {no one rostered, short of guards}. Runs in its own
     * transaction so a failure here cannot spoil the dashboard's own.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public int[] attentionCounts(TenantId tenantId, Instant now) {
        Board b = board(tenantId, null, 1, now);
        CoverageAttention.Counts c = CoverageAttention.count(b.rows().stream()
                .map(r -> new CoverageAttention.Item(r.state(), r.gaps().isEmpty() ? null : r.gaps().get(r.gaps().size() - 1).end()))
                .toList(), now);
        return new int[]{c.uncovered(), c.shortOfGuards()};
    }

    @Transactional(readOnly = true)
    public List<PostView> posts(TenantId tenantId, UUID siteId) {
        requireSite(tenantId.getValue(), siteId);
        return jdbc.query("""
                SELECT id, site_id, name, weekdays, start_time, end_time, min_guards FROM security_site_posts
                WHERE tenant_id = ? AND site_id = ? AND active ORDER BY name""",
                (rs, i) -> new PostView((UUID) rs.getObject("id"), (UUID) rs.getObject("site_id"), rs.getString("name"),
                        new ArrayList<>(weekdaysOf(rs.getArray("weekdays"))), rs.getObject("start_time", LocalTime.class),
                        rs.getObject("end_time", LocalTime.class), rs.getInt("min_guards")),
                tenantId.getValue(), siteId);
    }

    @Transactional
    public UUID addPost(TenantId tenantId, UUID siteId, UUID by, String name, List<Integer> weekdays, LocalTime start, LocalTime end, Integer minGuards) {
        requireSite(tenantId.getValue(), siteId);
        Set<Integer> days = CoverageRules.validate(name, weekdays == null ? null : new HashSet<>(weekdays), start, end, minGuards);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO security_site_posts (id, tenant_id, site_id, name, weekdays, start_time, end_time, min_guards, created_by)
                VALUES (?, ?, ?, ?, string_to_array(?, ',')::smallint[], ?, ?, ?, ?)""",
                id, tenantId.getValue(), siteId, name.strip(), String.join(",", days.stream().map(String::valueOf).toList()),
                java.sql.Time.valueOf(start), java.sql.Time.valueOf(end), minGuards, by);
        return id;
    }

    /** Stops measuring against a post. The row stays for history. */
    @Transactional
    public void deactivatePost(TenantId tenantId, UUID siteId, UUID postId, UUID by) {
        int n = jdbc.update("""
                UPDATE security_site_posts SET active = FALSE, deactivated_at = now(), deactivated_by = ?
                WHERE id = ? AND tenant_id = ? AND site_id = ? AND active""", by, postId, tenantId.getValue(), siteId);
        if (n == 0) throw new HandyFlowException("Post not found", HttpStatus.NOT_FOUND, "POST_NOT_FOUND");
    }

    private void requireSite(UUID tenant, UUID siteId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Integer.class, siteId, tenant);
        if (n == null || n == 0) throw new HandyFlowException("Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
    }

    private static Set<Integer> weekdaysOf(Array a) throws java.sql.SQLException {
        Set<Integer> out = new TreeSet<>();
        for (Object o : (Object[]) a.getArray()) out.add(((Number) o).intValue());
        return out;
    }

    private static java.sql.Timestamp utc(Instant i) { return java.sql.Timestamp.valueOf(LocalDateTime.ofInstant(i, ZoneOffset.UTC)); }
}
