// security/application/internal/GuardOverviewService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.repository.GuardScreeningRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse;
import za.co.handyflow.platform.security.application.internal.GuardReadinessCalculator.ScreeningFacts;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.Counts;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.EvidenceItem;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.Readiness;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.ReadinessItem;
import za.co.handyflow.platform.security.dto.GuardDocumentResponse;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.IncidentItem;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.ScreeningItem;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.ShiftItem;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the Guard 360 overview. Reuses GuardService for the guard and its
 * documents so access rules (tenant scoping, soft delete) stay in one place.
 */
@Service
@RequiredArgsConstructor
public class GuardOverviewService {

    static final int SHIFT_BACK_DAYS = 90;
    static final int SHIFT_FORWARD_DAYS = 14;
    static final int INCIDENT_BACK_DAYS = 180;
    static final int LIST_CAP = 50;

    private final GuardService guardService;
    private final GuardScreeningService screeningService;
    private final GuardScreeningEvidenceService evidenceService;
    private final GuardCompetencyService competencyService;
    private final GuardComplaintService complaintService;
    private final GuardScreeningRepository screeningRepository;
    private final ShiftRepository shiftRepository;
    private final IncidentRepository incidentRepository;
    private final SiteRepository siteRepository;

    @Transactional(readOnly = true)
    public GuardOverviewResponse overview(TenantId tenantId, UUID guardId) {
        return overview(tenantId, guardId, Instant.now());
    }

    /** Overload with an explicit clock so the windows can be tested. */
    @Transactional(readOnly = true)
    GuardOverviewResponse overview(TenantId tenantId, UUID guardId, Instant now) {
        var guard = guardService.getGuard(tenantId, guardId);
        var documents = guardService.getDocuments(tenantId, guardId);

        List<GuardScreeningRecord> records = screeningRepository.findByGuard(tenantId, guardId);
        Map<UUID, List<EvidenceItem>> evidence = new HashMap<>();
        for (GuardScreeningRecord r : records) {
            evidence.put(r.getId(), evidenceService.filesFor(tenantId, r.getId()).stream()
                    .map(e -> new EvidenceItem(e.id(), e.fileName(), e.evidenceType(), e.fileSizeBytes(), e.uploadedByName(), e.createdAt()))
                    .toList());
        }
        List<ScreeningItem> screening = records.stream()
                .map(r -> toItem(r, evidence.get(r.getId())))
                .toList();
        java.time.LocalDate today = now.atZone(java.time.ZoneId.of("Africa/Johannesburg")).toLocalDate();
        var competencies = competencyService.listForGuard(tenantId, guardId, today);
        Readiness readiness = readiness(guard.psiraNumber(), guard.psiraExpiryDate(), records, evidence, documents, competencies, now);

        var complaints = complaintService.forGuard(tenantId, guardId);

        Instant shiftFrom = now.minus(Duration.ofDays(SHIFT_BACK_DAYS));
        Instant shiftTo = now.plus(Duration.ofDays(SHIFT_FORWARD_DAYS));
        List<Shift> shifts = shiftRepository.findByGuardInRange(tenantId, guardId, shiftFrom, shiftTo);

        Instant incFrom = now.minus(Duration.ofDays(INCIDENT_BACK_DAYS));
        List<Incident> incidents = incidentRepository.findByGuardInRange(tenantId, guardId, incFrom, now);

        Map<UUID, String> siteNames = new HashMap<>();
        java.util.function.Function<UUID, String> siteName = id -> id == null ? null
                : siteNames.computeIfAbsent(id, k -> siteRepository.findActiveById(tenantId, k)
                        .map(s -> s.getName()).orElse(null));

        long past90 = shifts.stream().filter(s -> !s.getStartAt().isAfter(now)).count();
        long completed = shifts.stream()
                .filter(s -> !s.getStartAt().isAfter(now) && "COMPLETED".equals(s.getStatus().name()))
                .count();
        long open = incidents.stream().filter(i -> !"RESOLVED".equals(i.getStatus())).count();

        // Newest first, capped.
        List<ShiftItem> shiftItems = shifts.stream()
                .sorted(Comparator.comparing(Shift::getStartAt).reversed())
                .limit(LIST_CAP)
                .map(s -> new ShiftItem(s.getId(), s.getSiteId(), siteName.apply(s.getSiteId()),
                        s.getStartAt(), s.getEndAt(), s.getStatus().name(), s.getActualStartAt(),
                        ShiftPunctuality.minutesLate(s.getStartAt(), s.getActualStartAt())))
                .toList();
        List<IncidentItem> incidentItems = incidents.stream()
                .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                .limit(LIST_CAP)
                .map(i -> new IncidentItem(i.getId(), i.getSiteId(), siteName.apply(i.getSiteId()),
                        i.getTitle(), i.getSeverity(), i.getStatus(), i.getCreatedAt()))
                .toList();

        return new GuardOverviewResponse(
                guard,
                screeningService.checkScreeningGate(guardId),
                documents,
                screening,
                readiness,
                competencies,
                shiftItems,
                incidentItems,
                new Counts((int) past90, (int) completed, incidents.size(), (int) open),
                complaints,
                GuardComplaintService.counts(complaints, today));
    }

    /** Readiness is judged in South African time, like every other date rule in the product. */
    static Readiness readiness(String psiraNumber, java.time.LocalDate psiraExpiry, List<GuardScreeningRecord> records,
                               Map<UUID, List<EvidenceItem>> evidence, List<GuardDocumentResponse> documents,
                               List<za.co.handyflow.platform.security.dto.GuardCompetencyResponse> competencies, Instant now) {
        java.time.LocalDate today = now.atZone(java.time.ZoneId.of("Africa/Johannesburg")).toLocalDate();
        List<ScreeningFacts> facts = records.stream()
                .map(r -> new ScreeningFacts(r.getScreeningType().name(), r.getResult().name(), r.getConductedAt(),
                        r.getNextDueAt(), r.getCreatedAt(), evidence.getOrDefault(r.getId(), List.of()).size(), r.getDecision()))
                .toList();
        var categories = documents.stream().map(GuardDocumentResponse::category).collect(java.util.stream.Collectors.toSet());
        var comps = competencies.stream()
                .map(c -> new GuardReadinessCalculator.CompetencyFacts(c.id().toString(), c.competencyType(), c.title(), c.required(),
                        c.expiryDate(), c.evidence().size(), c.verifiedAt() != null))
                .toList();
        var result = GuardReadinessCalculator.calculate(
                new GuardReadinessCalculator.Input(today, psiraNumber, psiraExpiry, facts, categories, comps));

        // Link each screening row to its newest record so the page can open it.
        Map<String, UUID> newestId = new HashMap<>();
        Map<String, Instant> newestAt = new HashMap<>();
        for (GuardScreeningRecord r : records) {
            String t = r.getScreeningType().name();
            if (newestAt.get(t) == null || r.getCreatedAt().isAfter(newestAt.get(t))) { newestAt.put(t, r.getCreatedAt()); newestId.put(t, r.getId()); }
        }
        List<ReadinessItem> items = result.items().stream()
                .map(i -> new ReadinessItem(i.key(), i.label(), i.required(), i.state().name(), i.detail(), i.validUntil(),
                        i.evidenceCount(), i.met(), newestId.get(i.key()),
                        i.key().startsWith(GuardReadinessCalculator.COMPETENCY_PREFIX)
                                ? UUID.fromString(i.key().substring(GuardReadinessCalculator.COMPETENCY_PREFIX.length())) : null))
                .toList();
        return new Readiness(result.percent(), result.ready(), items, result.reasons());
    }

    private static ScreeningItem toItem(GuardScreeningRecord r, List<EvidenceItem> evidence) {
        return new ScreeningItem(r.getId(), r.getScreeningType().name(), r.getReason().name(),
                r.getResult().name(), r.getConductedBy(), r.getConductedAt(), r.getNextDueAt(),
                r.getReportRef(), r.getCreatedAt(),
                r.getProvider(), r.getRequestedAt(),
                r.getDecision(), r.getDecisionNote(), r.getDecidedByName(), r.getDecidedAt(),
                evidence);
    }
}
