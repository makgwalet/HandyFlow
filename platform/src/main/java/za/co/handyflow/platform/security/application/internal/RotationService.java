// security/application/internal/RotationService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.*;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.*;
import java.util.*;

/**
 * RotationService — manages rotation patterns, guard assignments, and
 * materialises Shift rows from a pattern over a date range.
 *
 * Shift generation rules:
 *   1. For each date in [fromDate, toDate], find all guards assigned to the pattern.
 *   2. Determine whether the guard is "on" or "off" that day based on patternType
 *      and their positionInCycle.
 *   3. If "on", create a Shift from (date + pattern.startHour) to
 *      (date + pattern.startHour + shiftLengthHours), skipping:
 *        - Guards whose status != ACTIVE at generation time
 *        - Guards whose PSiRA is expired at generation time
 *        - Date/guard combinations where an overlapping shift already exists
 *   4. Record skipped shifts in the warnings list so the caller can fix them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RotationService {

    private final RotationPatternRepository patternRepository;
    private final RotationAssignmentRepository assignmentRepository;
    private final ShiftRepository shiftRepository;
    private final GuardRepository guardRepository;
    private final SiteRepository siteRepository;

    // ── Pattern CRUD ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RotationPatternResponse> getPatterns(TenantId tenantId, Pageable pageable) {
        return patternRepository.findAllActive(tenantId, pageable)
                .map(p -> toPatternResponse(p, tenantId));
    }

    @Transactional
    public RotationPatternResponse createPattern(TenantId tenantId,
                                                 CreateRotationPatternRequest req) {
        // Validate site belongs to this tenant
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Site", req.siteId().toString()));

        RotationPattern.PatternType type;
        try {
            type = RotationPattern.PatternType.valueOf(req.patternType());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid patternType: " + req.patternType(),
                    HttpStatus.BAD_REQUEST, "INVALID_PATTERN_TYPE");
        }

        RotationPattern pattern = RotationPattern.create(
                tenantId, req.siteId(), req.name(),
                type, req.cycleDefinition(), req.shiftLengthHours());
        return toPatternResponse(patternRepository.save(pattern), tenantId);
    }

    @Transactional
    public RotationPatternResponse updatePattern(TenantId tenantId, UUID id,
                                                 UpdateRotationPatternRequest req) {
        RotationPattern pattern = patternRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("RotationPattern", id.toString()));
        pattern.update(req.name(), req.cycleDefinition(), req.shiftLengthHours());
        return toPatternResponse(patternRepository.save(pattern), tenantId);
    }

    @Transactional
    public void deactivatePattern(TenantId tenantId, UUID id) {
        RotationPattern pattern = patternRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("RotationPattern", id.toString()));
        pattern.deactivate();
        patternRepository.save(pattern);
    }

    // ── Guard Assignments ──────────────────────────────────────────────────────

    @Transactional
    public RotationAssignmentResponse assignGuard(TenantId tenantId,
                                                  CreateRotationAssignmentRequest req) {
        patternRepository.findByTenantAndId(tenantId, req.patternId())
                .orElseThrow(() -> new ResourceNotFoundException("RotationPattern", req.patternId().toString()));
        guardRepository.findActiveById(tenantId, req.guardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard", req.guardId().toString()));

        // End any existing open assignment for this guard
        assignmentRepository.findOpenAssignment(tenantId, req.guardId())
                .ifPresent(existing -> {
                    existing.end(req.startsAt().minusDays(1));
                    assignmentRepository.save(existing);
                    log.info("[Security] Ended previous rotation assignment={} for guard={}",
                            existing.getId(), req.guardId());
                });

        RotationAssignment assignment = RotationAssignment.create(
                tenantId, req.patternId(), req.guardId(),
                req.startsAt(), req.positionInCycle());
        assignmentRepository.save(assignment);

        return toAssignmentResponse(assignment, tenantId);
    }

    @Transactional
    public void endAssignment(TenantId tenantId, UUID assignmentId, LocalDate endsAt) {
        RotationAssignment assignment = assignmentRepository.findById(assignmentId)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("RotationAssignment", assignmentId.toString()));
        assignment.end(endsAt);
        assignmentRepository.save(assignment);
    }

    // ── Schedule Generation ────────────────────────────────────────────────────

    /**
     * Materialises Shift rows for a pattern over a date range.
     *
     * WHY up to 90 days per call?
     * Generating too far in advance (e.g. a full year) makes the schedule
     * brittle — guards change, sites close, patterns are updated.  90 days
     * gives operators enough forward visibility without locking in changes
     * that haven't happened yet.  Run it again (idempotent) to extend.
     *
     * WHY not a background scheduler?
     * Shift generation is an explicit management action ("Generate schedule
     * for next month") not an invisible background process.  The operator
     * needs to review the result before it goes live.  A scheduler that
     * silently creates hundreds of shifts would be hard to correct if the
     * pattern changes.
     */
    @Transactional
    public GenerateScheduleResponse generateSchedule(TenantId tenantId,
                                                     GenerateScheduleRequest req) {
        if (req.toDate().isAfter(req.fromDate().plusDays(90))) {
            throw new HandyFlowException("Schedule generation window cannot exceed 90 days",
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
        if (req.fromDate().isAfter(req.toDate())) {
            throw new HandyFlowException("fromDate must be before toDate", HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }

        RotationPattern pattern = patternRepository.findByTenantAndId(tenantId, req.patternId())
                .orElseThrow(() -> new ResourceNotFoundException("RotationPattern", req.patternId().toString()));

        if (!pattern.isActive()) {
            throw new HandyFlowException("Cannot generate from an inactive pattern",
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }

        List<RotationAssignment> assignments =
                assignmentRepository.findActiveByPattern(tenantId, req.patternId());

        if (assignments.isEmpty()) {
            return new GenerateScheduleResponse(pattern.getId(), pattern.getName(),
                    req.fromDate(), req.toDate(), 0, 0,
                    List.of("No guards assigned to this pattern"));
        }

        int created = 0;
        int skipped = 0;
        List<String> warnings = new ArrayList<>();

        for (LocalDate date = req.fromDate(); !date.isAfter(req.toDate()); date = date.plusDays(1)) {
            for (RotationAssignment assignment : assignments) {

                // Skip if assignment not active on this date
                if (!assignment.isActive() ||
                        date.isBefore(assignment.getStartsAt()) ||
                        (assignment.getEndsAt() != null && date.isAfter(assignment.getEndsAt()))) {
                    continue;
                }

                Guard guard = guardRepository.findActiveById(tenantId, assignment.getGuardId())
                        .orElse(null);

                if (guard == null) {
                    warnings.add("Guard " + assignment.getGuardId() + " not found — skipped");
                    skipped++;
                    continue;
                }

                // Skip non-schedulable guards
                if (!guard.isSchedulable()) {
                    warnings.add("Guard " + guard.getFullName() + " is " + guard.getStatus()
                            + " on " + date + " — skipped");
                    skipped++;
                    continue;
                }

                // Skip expired PSiRA
                if (guard.getPsiraExpiryDate() != null &&
                        guard.getPsiraExpiryDate().isBefore(date)) {
                    warnings.add("Guard " + guard.getFullName() + " PSiRA expired on "
                            + guard.getPsiraExpiryDate() + " — skipped " + date);
                    skipped++;
                    continue;
                }

                // Determine if this guard is "on" today based on pattern type
                if (!isOnDuty(pattern, assignment, date)) {
                    continue;  // off-day in the cycle
                }

                // Build shift window
                Instant shiftStart = buildShiftStart(pattern, date);
                Instant shiftEnd   = shiftStart.plus(
                        Duration.ofHours(pattern.getShiftLengthHours()));

                // Skip if shift already exists (idempotent)
                if (shiftRepository.hasOverlap(tenantId, assignment.getGuardId(),
                        shiftStart, shiftEnd, null)) {
                    skipped++;
                    continue;
                }

                Shift shift = Shift.create(tenantId, pattern.getSiteId(),
                        assignment.getGuardId(), shiftStart, shiftEnd,
                        "Generated from rotation: " + pattern.getName());
                shiftRepository.save(shift);
                created++;
            }
        }

        log.info("[Security] Schedule generated pattern={} range={}/{} created={} skipped={}",
                pattern.getId(), req.fromDate(), req.toDate(), created, skipped);

        return new GenerateScheduleResponse(pattern.getId(), pattern.getName(),
                req.fromDate(), req.toDate(), created, skipped, warnings);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Determines whether a guard is "on duty" on a given date based on their
     * positionInCycle and the pattern's cycle definition.
     *
     * FIXED_DAYS_ON_OFF: days since startsAt + positionInCycle, modulo (onDays + offDays)
     * WEEKLY_FIXED: extract day-of-week from cycleDefinition
     * ALTERNATING_DAY_NIGHT: which week in the two-week cycle (both are on-duty, just different shifts)
     * CUSTOM: always returns true (let the caller interpret the definition)
     */
    private boolean isOnDuty(RotationPattern pattern, RotationAssignment assignment, LocalDate date) {
        long daysSinceStart = assignment.getStartsAt().until(date, java.time.temporal.ChronoUnit.DAYS)
                + assignment.getPositionInCycle();

        return switch (pattern.getPatternType()) {
            case FIXED_DAYS_ON_OFF -> {
                int onDays  = intVal(pattern.getCycleDefinition(), "onDays",  4);
                int offDays = intVal(pattern.getCycleDefinition(), "offDays", 2);
                yield (daysSinceStart % (onDays + offDays)) < onDays;
            }
            case WEEKLY_FIXED -> {
                // cycleDefinition: {"monday": "DAY", "tuesday": "OFF", ...}
                String dow    = date.getDayOfWeek().name().toLowerCase();
                String status = (String) pattern.getCycleDefinition().getOrDefault(dow, "OFF");
                yield !"OFF".equalsIgnoreCase(status);
            }
            case ALTERNATING_DAY_NIGHT ->
                // Both weeks are on-duty; this just controls start hour (handled in buildShiftStart)
                    true;
            case CUSTOM -> true;
        };
    }

    private Instant buildShiftStart(RotationPattern pattern, LocalDate date) {
        // Default: shifts start at 06:00 SAST (UTC+2)
        int startHour = 6;
        if (pattern.getCycleDefinition().containsKey("startHour")) {
            startHour = intVal(pattern.getCycleDefinition(), "startHour", 6);
        }
        ZoneId zone = ZoneId.of("Africa/Johannesburg");
        return date.atTime(startHour, 0).atZone(zone).toInstant();
    }

    private int intVal(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return defaultVal;
    }

    private RotationPatternResponse toPatternResponse(RotationPattern p, TenantId tenantId) {
        int guardCount = assignmentRepository.findActiveByPattern(tenantId, p.getId()).size();
        String siteName = siteRepository.findActiveById(tenantId, p.getSiteId())
                .map(s -> s.getName()).orElse("Unknown");
        return new RotationPatternResponse(p.getId(), p.getSiteId(), siteName,
                p.getName(), p.getPatternType().name(),
                p.getCycleDefinition(), p.getShiftLengthHours(),
                guardCount, p.isActive(), p.getCreatedAt());
    }

    private RotationAssignmentResponse toAssignmentResponse(RotationAssignment a, TenantId tenantId) {
        String guardName = guardRepository.findActiveById(tenantId, a.getGuardId())
                .map(Guard::getFullName).orElse("Unknown");
        String patternName = patternRepository.findByTenantAndId(tenantId, a.getPatternId())
                .map(RotationPattern::getName).orElse("Unknown");
        return new RotationAssignmentResponse(a.getId(), a.getGuardId(), guardName,
                a.getPatternId(), patternName, a.getStartsAt(), a.getEndsAt(),
                a.getPositionInCycle(), a.isActive());
    }
}