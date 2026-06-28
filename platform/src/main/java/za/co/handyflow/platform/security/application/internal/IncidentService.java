// security/application/internal/IncidentService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.CreateIncidentRequest;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * IncidentService — rewritten to fix bugs #2, #8, #12, #14, #16, #20.
 *
 * The original implementation loaded ALL incidents for the tenant into memory,
 * filtered in Java, then ran two extra SQL queries per incident to resolve
 * site name and guard name (N+1).  At even modest volumes (a few thousand
 * incidents per tenant after a year of operation) this was a guaranteed
 * full-table scan + heap explosion.
 *
 * Fix strategy (same as BookingsService.getBookings):
 * - Single JDBC query with LEFT JOIN to resolve site_name and guard full name.
 * - WHERE clause built dynamically for status/severity filters.
 * - COUNT query for pagination total (only runs once, not per row).
 * - ORDER BY in SQL (fixes bug #12 — sort was silently ignored before).
 * - Tenant validation of guardId/siteId on createIncident (fixes bug #14).
 * - incident.type now set from CreateIncidentRequest (fixes bug #16).
 * - acknowledgedBy/resolvedBy captured from the caller (fixes bug #20).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepo;
    private final GuardRepository    guardRepo;
    private final SiteRepository     siteRepo;
    private final JdbcTemplate       jdbc;

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns a paginated, sorted list of incidents with site and guard names
     * resolved in a single SQL query.
     *
     * WHY JdbcTemplate instead of a JPA @Query?
     * Dynamic WHERE clauses (optional status, optional severity, pagination,
     * sorting) are verbose and fragile with JPA.  JDBC gives us full SQL
     * control, eliminates the ORM overhead on a list-only query, and avoids
     * the N+1 pattern from the original fetchSiteName/fetchGuardName calls.
     */
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidents(TenantId tenantId,
                                               String status,
                                               String severity,
                                               Pageable pageable) {
        String baseSelect = """
                SELECT
                    i.id, i.site_id, ss.name AS site_name,
                    i.shift_id, i.guard_id,
                    (g.first_name || ' ' || g.last_name) AS guard_name,
                    i.title, i.description, i.severity, i.status,
                    i.latitude, i.longitude,
                    i.acknowledged_at, i.resolved_at,
                    i.created_at, i.updated_at
                FROM security_incidents i
                LEFT JOIN security_sites  ss ON ss.id = i.site_id
                LEFT JOIN security_guards g  ON g.id  = i.guard_id
                WHERE i.tenant_id = ?
                """;

        List<Object> params = new ArrayList<>();
        params.add(tenantId.getValue());

        StringBuilder where = new StringBuilder();
        if (status != null && !status.isBlank()) {
            where.append(" AND i.status = ?");
            params.add(status.toUpperCase());
        }
        if (severity != null && !severity.isBlank()) {
            where.append(" AND i.severity = ?");
            params.add(severity.toUpperCase());
        }

        // Sorting — respect pageable.getSort(); fall back to newest-first.
        // WHY support sort here? Bug #12: the original in-memory impl silently
        // ignored ?sort= params from the frontend/API consumers.
        String orderBy = " ORDER BY i.created_at DESC";
        if (pageable.getSort().isSorted()) {
            var order = pageable.getSort().iterator().next();
            String col = switch (order.getProperty()) {
                case "severity"  -> "i.severity";
                case "status"    -> "i.status";
                case "siteName"  -> "ss.name";
                case "guardName" -> "guard_name";
                default          -> "i.created_at";
            };
            orderBy = " ORDER BY " + col + (order.isAscending() ? " ASC" : " DESC");
        }

        String countSql = "SELECT COUNT(*) FROM security_incidents i WHERE i.tenant_id = ?" + where;
        String pageSql  = baseSelect + where + orderBy + " LIMIT ? OFFSET ?";

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageable.getPageSize());
        pageParams.add(pageable.getOffset());

        List<IncidentResponse> rows = jdbc.query(pageSql, this::mapRow, pageParams.toArray());
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());

        return new PageImpl<>(rows, pageable, total != null ? total : 0L);
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public IncidentResponse createIncident(TenantId tenantId, CreateIncidentRequest req) {
        // Fix bug #14: validate that siteId and guardId belong to this tenant.
        // Without this, a crafted request could link a site/guard from another
        // tenant, corrupting cross-tenant reporting.
        siteRepo.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Site", req.siteId().toString()));

        if (req.guardId() != null) {
            guardRepo.findActiveById(tenantId, req.guardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Guard", req.guardId().toString()));
        }

        Incident incident = Incident.create(
                tenantId,
                req.siteId(),
                req.shiftId(),
                req.guardId(),
                req.title(),
                req.description(),
                req.severity(),
                req.latitude(),
                req.longitude()
        );

        // Fix bug #16: type column was never set.
        // CreateIncidentRequest now includes an optional type; default to GENERAL.
        // We use JDBC directly to set the type column because Incident.java
        // doesn't have a type field (it was designed after V12 introduced the column).
        // A cleaner fix would add type to the Incident entity — do that in Phase 1
        // when we refactor the entity model.  For Phase 0 we set it via JDBC after save.
        incidentRepo.save(incident);

        String incidentType = (req.type() != null && !req.type().isBlank())
                ? req.type().toUpperCase() : "GENERAL";
        jdbc.update("UPDATE security_incidents SET type = ? WHERE id = ?",
                incidentType, incident.getId());

        log.info("[Security] Incident created id={} type={} severity={} site={}",
                incident.getId(), incidentType, req.severity(), req.siteId());
        return getIncidentById(tenantId, incident.getId());
    }

    @Transactional
    public IncidentResponse acknowledge(TenantId tenantId, UUID incidentId, UUID acknowledgedBy) {
        Incident incident = findIncident(tenantId, incidentId);
        if ("RESOLVED".equalsIgnoreCase(incident.getStatus())) {
            throw new HandyFlowException(
                    "Cannot acknowledge a resolved incident",
                    HttpStatus.BAD_REQUEST, "ALREADY_RESOLVED");
        }
        incident.acknowledge();
        incidentRepo.save(incident);

        // Fix bug #20: record WHO acknowledged, not just when.
        // Done via JDBC because the Incident entity doesn't yet have acknowledgedBy field.
        // Add it to Incident.java in Phase 1 alongside a proper audit event table.
        if (acknowledgedBy != null) {
            jdbc.update("UPDATE security_incidents SET acknowledged_by = ? WHERE id = ?",
                    acknowledgedBy, incidentId);
        }

        log.info("[Security] Incident acknowledged id={} by={}", incidentId, acknowledgedBy);
        return getIncidentById(tenantId, incidentId);
    }

    @Transactional
    public IncidentResponse resolve(TenantId tenantId, UUID incidentId, UUID resolvedBy) {
        Incident incident = findIncident(tenantId, incidentId);
        if ("RESOLVED".equalsIgnoreCase(incident.getStatus())) {
            throw new HandyFlowException(
                    "Incident is already resolved",
                    HttpStatus.BAD_REQUEST, "ALREADY_RESOLVED");
        }
        incident.resolve();
        incidentRepo.save(incident);

        // Fix bug #20: record WHO resolved.
        if (resolvedBy != null) {
            jdbc.update("UPDATE security_incidents SET resolved_by = ? WHERE id = ?",
                    resolvedBy, incidentId);
        }

        log.info("[Security] Incident resolved id={} by={}", incidentId, resolvedBy);
        return getIncidentById(tenantId, incidentId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Incident findIncident(TenantId tenantId, UUID id) {
        return incidentRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id.toString()));
    }

    private IncidentResponse getIncidentById(TenantId tenantId, UUID id) {
        String sql = """
                SELECT
                    i.id, i.site_id, ss.name AS site_name,
                    i.shift_id, i.guard_id,
                    (g.first_name || ' ' || g.last_name) AS guard_name,
                    i.title, i.description, i.severity, i.status,
                    i.latitude, i.longitude,
                    i.acknowledged_at, i.resolved_at,
                    i.created_at, i.updated_at
                FROM security_incidents i
                LEFT JOIN security_sites  ss ON ss.id = i.site_id
                LEFT JOIN security_guards g  ON g.id  = i.guard_id
                WHERE i.id = ? AND i.tenant_id = ?
                """;
        return jdbc.queryForObject(sql, this::mapRow, id, tenantId.getValue());
    }

    private IncidentResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        String guardIdStr = rs.getString("guard_id");
        String shiftIdStr = rs.getString("shift_id");

        Timestamp ackedTs    = rs.getTimestamp("acknowledged_at");
        Timestamp resolvedTs = rs.getTimestamp("resolved_at");
        Timestamp createdTs  = rs.getTimestamp("created_at");
        Timestamp updatedTs  = rs.getTimestamp("updated_at");

        return new IncidentResponse(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("site_id")),
                rs.getString("site_name"),           // from LEFT JOIN — no extra query
                shiftIdStr != null ? UUID.fromString(shiftIdStr) : null,
                guardIdStr != null ? UUID.fromString(guardIdStr) : null,
                rs.getString("guard_name"),          // from LEFT JOIN — no extra query
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                ackedTs    != null ? ackedTs.toInstant()    : null,
                resolvedTs != null ? resolvedTs.toInstant() : null,
                createdTs  != null ? createdTs.toInstant()  : null,
                updatedTs  != null ? updatedTs.toInstant()  : null
        );
    }
}
