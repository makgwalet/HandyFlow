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
import za.co.handyflow.platform.security.application.internal.CheckpointScanService;
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
 * IncidentService — see original class javadoc for the bug-fix history
 * (#2, #8, #12, #14, #16, #20).
 *
 * CHANGE: added getIncidentDetail() (public) -- backs the new incident PDF
 * endpoint (IncidentController.getIncidentPdf() -> IncidentPdfService).
 * Delegates straight to the existing private getIncidentById() JDBC fetch,
 * which already scopes by tenantId -- no new query needed, just a public
 * entry point that previously didn't exist (this service only ever returned
 * a single incident as a side effect of createIncident/acknowledge/resolve,
 * never as a direct "fetch by id" read).
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
                    i.title, i.description, i.severity, i.status, i.type,
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

    /**
     * Single-incident fetch — added to back the incident PDF endpoint.
     * Throws ResourceNotFoundException (via the underlying queryForObject's
     * EmptyResultDataAccessException translation) if no matching incident
     * exists for this tenant, same as every other findXxx in this codebase.
     */
    @Transactional(readOnly = true)
    public IncidentResponse getIncidentDetail(TenantId tenantId, UUID id) {
        return getIncidentById(tenantId, id);
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public IncidentResponse createIncident(TenantId tenantId, CreateIncidentRequest req) {
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

        if (req.latitude() != null && req.longitude() != null) {
            siteRepo.findActiveById(tenantId, req.siteId()).ifPresent(site -> {
                if (site.getLatitude() != null && site.getLongitude() != null) {
                    double dist = CheckpointScanService.haversineMetres(
                            req.latitude(), req.longitude(),
                            site.getLatitude(), site.getLongitude());
                    if (dist > 2000) {
                        log.warn("[Security] Incident GPS {}m from site '{}' — guard may not be on-site",
                                Math.round(dist), site.getName());
                    }
                }
            });
        }

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
                    i.title, i.description, i.severity, i.status, i.type,
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
                rs.getString("site_name"),
                shiftIdStr != null ? UUID.fromString(shiftIdStr) : null,
                guardIdStr != null ? UUID.fromString(guardIdStr) : null,
                rs.getString("guard_name"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getString("type"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                ackedTs    != null ? ackedTs.toInstant()    : null,
                resolvedTs != null ? resolvedTs.toInstant() : null,
                createdTs  != null ? createdTs.toInstant()  : null,
                updatedTs  != null ? updatedTs.toInstant()  : null
        );
    }
}