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
import org.springframework.util.StringUtils;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.dto.CreateIncidentRequest;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepo;
    private final JdbcTemplate       jdbc;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidents(TenantId tenantId,
                                               String status,
                                               String severity,
                                               Pageable pageable) {

        List<Incident> all = incidentRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);

        // In-memory filtering — replace with a @Query predicate if volume grows
        Stream<Incident> stream = all.stream();
        if (StringUtils.hasText(status)) {
            stream = stream.filter(i -> status.equalsIgnoreCase(i.getStatus()));
        }
        if (StringUtils.hasText(severity)) {
            stream = stream.filter(i -> severity.equalsIgnoreCase(i.getSeverity()));
        }

        List<Incident> filtered = stream.toList();
        int start   = (int) pageable.getOffset();
        int end     = Math.min(start + pageable.getPageSize(), filtered.size());
        List<IncidentResponse> page = start >= filtered.size()
                ? List.of()
                : filtered.subList(start, end).stream().map(this::toResponse).toList();

        return new PageImpl<>(page, pageable, filtered.size());
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public IncidentResponse createIncident(TenantId tenantId, CreateIncidentRequest req) {
        // Convert Double GPS coords to BigDecimal (fixes CheckpointScanService compile error pattern)
        BigDecimal lat = req.latitude()  != null ? req.latitude()  : null;
        BigDecimal lon = req.longitude() != null ? req.longitude() : null;

        Incident incident = Incident.create(
                tenantId,
                req.siteId(),
                req.shiftId(),
                req.guardId(),
                req.title(),
                req.description(),
                req.severity(),
                lat,
                lon
        );
        incidentRepo.save(incident);
        log.info("Incident created id={} severity={} site={}", incident.getId(), req.severity(), req.siteId());
        return toResponse(incident);
    }

    @Transactional
    public IncidentResponse acknowledge(TenantId tenantId, UUID incidentId) {
        Incident incident = findIncident(tenantId, incidentId);
        if ("RESOLVED".equalsIgnoreCase(incident.getStatus())) {
            throw new HandyFlowException(
                    "Cannot acknowledge a resolved incident", HttpStatus.BAD_REQUEST, "ALREADY_RESOLVED");
        }
        incident.acknowledge();
        incidentRepo.save(incident);
        log.info("Incident acknowledged id={}", incidentId);
        return toResponse(incident);
    }

    @Transactional
    public IncidentResponse resolve(TenantId tenantId, UUID incidentId) {
        Incident incident = findIncident(tenantId, incidentId);
        if ("RESOLVED".equalsIgnoreCase(incident.getStatus())) {
            throw new HandyFlowException(
                    "Incident is already resolved", HttpStatus.BAD_REQUEST, "ALREADY_RESOLVED");
        }
        incident.resolve();
        incidentRepo.save(incident);
        log.info("Incident resolved id={}", incidentId);
        return toResponse(incident);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Incident findIncident(TenantId tenantId, UUID id) {
        return incidentRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id.toString()));
    }

    private String fetchSiteName(UUID siteId) {
        if (siteId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT name FROM security_sites WHERE id = ?", String.class, siteId);
        } catch (Exception e) { return null; }
    }

    private String fetchGuardName(UUID guardId) {
        if (guardId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT first_name || ' ' || last_name FROM security_guards WHERE id = ?",
                    String.class, guardId);
        } catch (Exception e) { return null; }
    }

    private IncidentResponse toResponse(Incident i) {
        return new IncidentResponse(
                i.getId(),
                i.getSiteId(),
                fetchSiteName(i.getSiteId()),
                i.getShiftId(),
                i.getGuardId(),
                fetchGuardName(i.getGuardId()),
                i.getTitle(),
                i.getDescription(),
                i.getSeverity(),
                i.getStatus(),
                i.getLatitude(),
                i.getLongitude(),
                i.getAcknowledgedAt(),
                i.getResolvedAt(),
                i.getCreatedAt(),
                i.getUpdatedAt()
        );
    }
}
