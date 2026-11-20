package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.application.internal.DeadlineCalculator.Calendar;
import za.co.handyflow.platform.legalpractice.application.internal.DeadlineCalculator.*;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Deadline rules, the firm's court calendar, and computed deadlines on matters. A computation creates (or, when corrected,
 * moves) an ordinary matter key date, so the diary, the calendar and the reminder job need no changes.
 */
@Service
@RequiredArgsConstructor
public class LpDeadlineService {

    /** Trigger dates are limited to the years the holiday rules cover. */
    static final LocalDate EARLIEST_TRIGGER = LocalDate.of(2000, 1, 1);
    static final LocalDate LATEST_TRIGGER = LocalDate.of(2100, 12, 31);

    private final LpDeadlineRuleRepository ruleRepo;
    private final LpCourtCalendarEntryRepository calendarRepo;
    private final LpDeadlineComputationRepository computationRepo;
    private final LpMatterRepository matterRepo;
    private final LpMatterKeyDateRepository keyDateRepo;
    private final LpWallGuard walls;

    // ── rules ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DeadlineRuleResponse> listRules(TenantId tenantId, boolean includeInactive) {
        return ruleRepo.findAllForTenant(tenantId).stream()
                .filter(r -> includeInactive || r.isActive()).map(LpDeadlineService::toResponse).toList();
    }

    @Transactional
    public DeadlineRuleResponse createRule(TenantId tenantId, DeadlineRuleRequest req) {
        Clean c = clean(req);
        if (ruleRepo.findByCode(tenantId, c.code).isPresent()) throw new IllegalStateException("A rule with the code " + c.code + " already exists");
        LpDeadlineRule r = LpDeadlineRule.create(tenantId, c.code, c.name, c.description, c.court, c.triggerLabel, c.keyDateType,
                c.amount, c.unit, c.direction, c.rollover, c.recessStart, c.recessEnd, c.sourceReference);
        ruleRepo.save(r);
        return toResponse(r);
    }

    @Transactional
    public DeadlineRuleResponse updateRule(TenantId tenantId, UUID id, DeadlineRuleRequest req) {
        LpDeadlineRule r = findRule(tenantId, id);
        Clean c = clean(req);
        Optional<LpDeadlineRule> sameCode = ruleRepo.findByCode(tenantId, c.code);
        if (sameCode.isPresent() && !sameCode.get().getId().equals(r.getId())) throw new IllegalStateException("A rule with the code " + c.code + " already exists");
        r.update(c.code, c.name, c.description, c.court, c.triggerLabel, c.keyDateType, c.amount, c.unit, c.direction, c.rollover,
                c.recessStart, c.recessEnd, c.sourceReference);
        ruleRepo.save(r);
        return toResponse(r);
    }

    @Transactional
    public DeadlineRuleResponse setRuleActive(TenantId tenantId, UUID id, boolean active) {
        LpDeadlineRule r = findRule(tenantId, id);
        r.setActive(active);
        ruleRepo.save(r);
        return toResponse(r);
    }

    /** Adds the template rules the firm does not have yet (matched by code); never touches an existing rule. */
    @Transactional
    public InstallTemplatesResponse installTemplates(TenantId tenantId) {
        List<String> added = new ArrayList<>();
        int there = 0;
        for (DeadlineRuleTemplates.Template t : DeadlineRuleTemplates.ALL) {
            if (ruleRepo.findByCode(tenantId, t.code()).isPresent()) { there++; continue; }
            DeadlineCalculator.validate(ruleOf(t.amount(), t.unit(), t.direction(), t.rollover(), t.recessStart(), t.recessEnd()));
            ruleRepo.save(LpDeadlineRule.create(tenantId, t.code(), t.name(), t.description(), t.court(), t.triggerLabel(), t.keyDateType(),
                    t.amount(), t.unit(), t.direction(), t.rollover(), t.recessStart(), t.recessEnd(), t.sourceReference()));
            added.add(t.code());
        }
        return new InstallTemplatesResponse(added.size(), there, added);
    }

    // ── court calendar ──────────────────────────────────────────────────────

    /** The statutory holidays of {@code year} together with the firm's own entries for it, in date order. */
    @Transactional(readOnly = true)
    public List<CalendarDayResponse> calendar(TenantId tenantId, int year) {
        if (year < SaPublicHolidays.FIRST_YEAR || year > 2100) throw new IllegalArgumentException("Choose a year from " + SaPublicHolidays.FIRST_YEAR + " to 2100");
        List<CalendarDayResponse> out = new ArrayList<>();
        SaPublicHolidays.forYear(year).forEach((d, n) -> out.add(new CalendarDayResponse(null, d, "PUBLIC_HOLIDAY", n, "STATUTORY")));
        for (LpCourtCalendarEntry e : calendarRepo.findInRange(tenantId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
            out.add(new CalendarDayResponse(e.getId(), e.getEntryDate(), e.getKind(), e.getReason(), "FIRM"));
        }
        out.sort(Comparator.comparing(CalendarDayResponse::date).thenComparing(CalendarDayResponse::source));
        return out;
    }

    @Transactional
    public CalendarDayResponse addCalendarEntry(TenantId tenantId, CalendarEntryRequest req, UUID by, String byName) {
        if (req.date().isBefore(EARLIEST_TRIGGER) || req.date().isAfter(LATEST_TRIGGER)) throw new IllegalArgumentException("Choose a date between 2000 and 2100");
        if (calendarRepo.findOnDate(tenantId, req.date(), req.kind()).isPresent()) throw new IllegalStateException("That day is already in the calendar as " + req.kind());
        LpCourtCalendarEntry e = LpCourtCalendarEntry.create(tenantId, req.date(), req.kind(), req.reason(), by, byName);
        calendarRepo.save(e);
        return new CalendarDayResponse(e.getId(), e.getEntryDate(), e.getKind(), e.getReason(), "FIRM");
    }

    /**
     * Removes a firm entry. Dates already computed keep their explanation and are not recalculated: a person recomputes the
     * ones that matter.
     */
    @Transactional
    public void removeCalendarEntry(TenantId tenantId, UUID id) {
        calendarRepo.delete(calendarRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpCourtCalendarEntry", id.toString())));
    }

    // ── computing ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DeadlinePreviewResponse preview(TenantId tenantId, DeadlinePreviewRequest req) {
        LpDeadlineRule rule = findRule(tenantId, req.ruleId());
        Result res = run(tenantId, req.triggerDate(), ruleOf(rule.getAmount(), rule.getUnit(), rule.getDirection(), rule.getRollover(), rule.getRecessStart(), rule.getRecessEnd()));
        return new DeadlinePreviewResponse(rule.getId(), rule.getName(), rule.getTriggerLabel(), req.triggerDate(), res.date(), dayName(res.date()), res.explain());
    }

    /** Works out the date and records it as a new key date on the matter. */
    @Transactional
    public DeadlineComputationResponse compute(TenantId tenantId, UUID matterId, ComputeDeadlineRequest req, UUID by, String byName) {
        LpMatter matter = matterRepo.findActiveById(tenantId, matterId).orElseThrow(() -> new ResourceNotFoundException("LpMatter", matterId.toString()));
        walls.requireAccess(tenantId, matter);
        LpDeadlineRule rule = findRule(tenantId, req.ruleId());
        if (!rule.isActive()) throw new IllegalStateException("That rule is switched off");
        // The same rule and trigger date on the same matter, still pending, is almost certainly a double click.
        for (LpDeadlineComputation prior : computationRepo.findAllForMatter(tenantId, matterId)) {
            if (prior.getRuleCode().equalsIgnoreCase(rule.getCode()) && prior.getTriggerDate().equals(req.triggerDate())
                    && computationRepo.countSuccessors(tenantId, prior.getId()) == 0) {
                Optional<LpMatterKeyDate> k = keyDateRepo.findActiveById(tenantId, prior.getKeyDateId());
                if (k.isPresent() && "PENDING".equals(k.get().getStatus())) {
                    throw new IllegalStateException("This deadline was already worked out for this matter (" + prior.getDueDate() + "). Correct that one instead of adding another.");
                }
            }
        }
        Result res = run(tenantId, req.triggerDate(), ruleOf(rule.getAmount(), rule.getUnit(), rule.getDirection(), rule.getRollover(), rule.getRecessStart(), rule.getRecessEnd()));
        String description = req.description() == null || req.description().isBlank() ? rule.getName() : req.description().trim();
        LpMatterKeyDate keyDate = LpMatterKeyDate.create(tenantId, matterId, rule.getKeyDateType(), res.date(), description,
                note(rule.getName(), rule.getTriggerLabel(), req.triggerDate(), req.notes()));
        keyDateRepo.save(keyDate);
        LpDeadlineComputation c = LpDeadlineComputation.of(tenantId, matterId, keyDate.getId(), null, rule.getId(), rule.getCode(), rule.getName(),
                rule.getAmount(), rule.getUnit(), rule.getDirection(), rule.getRollover(), rule.getRecessStart(), rule.getRecessEnd(),
                rule.getTriggerLabel(), req.triggerDate(), res.date(), String.join("\n", res.explain()), null, by, byName);
        computationRepo.save(c);
        return toResponse(c, true, keyDate);
    }

    /**
     * Corrects a computed date (usually because the trigger date was wrong). Adds a new computation that supersedes the old
     * one and moves the key date; the key date must still be pending and the computation must be the newest.
     */
    @Transactional
    public DeadlineComputationResponse recompute(TenantId tenantId, UUID computationId, RecomputeDeadlineRequest req, UUID by, String byName) {
        LpDeadlineComputation old = computationRepo.findActiveById(tenantId, computationId)
                .orElseThrow(() -> new ResourceNotFoundException("LpDeadlineComputation", computationId.toString()));
        walls.requireMatterAccess(tenantId, old.getMatterId());
        if (computationRepo.countSuccessors(tenantId, old.getId()) > 0) throw new IllegalStateException("This computation has already been corrected; correct the latest one");
        LpMatterKeyDate keyDate = keyDateRepo.findActiveById(tenantId, old.getKeyDateId())
                .orElseThrow(() -> new ResourceNotFoundException("LpMatterKeyDate", old.getKeyDateId().toString()));
        if (!"PENDING".equals(keyDate.getStatus())) throw new IllegalStateException("The key date is already " + keyDate.getStatus().toLowerCase(Locale.ENGLISH) + " and cannot be moved");
        if (req.reason() == null || req.reason().trim().length() < LpDeadlineComputation.MIN_REASON) {
            throw new IllegalArgumentException("Say why the date is being corrected (at least " + LpDeadlineComputation.MIN_REASON + " characters)");
        }

        UUID ruleId = old.getRuleId();
        String code = old.getRuleCode(), name = old.getRuleName(), label = old.getTriggerLabel();
        int amount = old.getAmount();
        String unit = old.getUnit(), dir = old.getDirection(), roll = old.getRollover(), rs = old.getRecessStart(), re = old.getRecessEnd();
        if (req.useCurrentRule()) {
            LpDeadlineRule cur = old.getRuleId() == null ? null : ruleRepo.findActiveById(tenantId, old.getRuleId()).orElse(null);
            if (cur == null) throw new IllegalStateException("The rule no longer exists; correct with the original rule instead");
            if (!cur.isActive()) throw new IllegalStateException("That rule is switched off");
            code = cur.getCode(); name = cur.getName(); label = cur.getTriggerLabel(); amount = cur.getAmount();
            unit = cur.getUnit(); dir = cur.getDirection(); roll = cur.getRollover(); rs = cur.getRecessStart(); re = cur.getRecessEnd();
        }
        Result res = run(tenantId, req.triggerDate(), ruleOf(amount, unit, dir, roll, rs, re));
        keyDate.update(keyDate.getDateType(), res.date(), keyDate.getDescription(), note(name, label, req.triggerDate(), keyDate.getNotes() == null ? null : stripComputedLine(keyDate.getNotes())));
        keyDateRepo.save(keyDate);
        LpDeadlineComputation c = LpDeadlineComputation.of(tenantId, old.getMatterId(), keyDate.getId(), old.getId(), ruleId, code, name, amount, unit, dir,
                roll, rs, re, label, req.triggerDate(), res.date(), String.join("\n", res.explain()), req.reason(), by, byName);
        computationRepo.save(c);
        return toResponse(c, true, keyDate);
    }

    @Transactional(readOnly = true)
    public List<DeadlineComputationResponse> listForMatter(TenantId tenantId, UUID matterId) {
        walls.requireMatterAccess(tenantId, matterId);
        Map<UUID, LpMatterKeyDate> keyDates = new HashMap<>();
        keyDateRepo.findAllForMatter(tenantId, matterId).forEach(k -> keyDates.put(k.getId(), k));
        List<LpDeadlineComputation> all = computationRepo.findAllForMatter(tenantId, matterId);
        Set<UUID> superseded = new HashSet<>();
        all.forEach(c -> { if (c.getSupersedesId() != null) superseded.add(c.getSupersedesId()); });
        return all.stream().map(c -> toResponse(c, !superseded.contains(c.getId()), keyDates.get(c.getKeyDateId()))).toList();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Result run(TenantId tenantId, LocalDate trigger, Rule rule) {
        if (trigger == null) throw new IllegalArgumentException("The trigger date is required");
        if (trigger.isBefore(EARLIEST_TRIGGER) || trigger.isAfter(LATEST_TRIGGER)) throw new IllegalArgumentException("The trigger date must be between 2000 and 2100");
        Map<LocalDate, String> holidays = new HashMap<>(), closures = new HashMap<>();
        for (LpCourtCalendarEntry e : calendarRepo.findInRange(tenantId, trigger.minusYears(32), trigger.plusYears(32))) {
            ("PUBLIC_HOLIDAY".equals(e.getKind()) ? holidays : closures).put(e.getEntryDate(), e.getReason());
        }
        return DeadlineCalculator.compute(trigger, rule, new Calendar() {
            @Override public String publicHoliday(LocalDate d) {
                String statutory = SaPublicHolidays.nameOf(d);
                return statutory != null ? statutory : holidays.get(d);
            }
            @Override public String courtClosure(LocalDate d) { return closures.get(d); }
        });
    }

    static Rule ruleOf(int amount, String unit, String direction, String rollover, String recessStart, String recessEnd) {
        try {
            return new Rule(amount, Unit.valueOf(unit), Direction.valueOf(direction), Rollover.valueOf(rollover), monthDay(recessStart), monthDay(recessEnd));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("No enum constant")) throw new IllegalArgumentException("Unknown unit, direction or rollover: " + e.getMessage().substring(e.getMessage().lastIndexOf('.') + 1));
            throw e;
        }
    }

    private static MonthDay monthDay(String s) {
        if (s == null || s.isBlank()) return null;
        if (!s.matches("^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$")) throw new IllegalArgumentException("Recess dates are written MM-DD, for example 12-16");
        try { return MonthDay.parse("--" + s); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Recess date " + s + " is not a real day"); }
    }

    private record Clean(String code, String name, String description, String court, String triggerLabel, String keyDateType, int amount,
                         String unit, String direction, String rollover, String recessStart, String recessEnd, String sourceReference) {}

    private Clean clean(DeadlineRuleRequest r) {
        String keyDateType = r.keyDateType().trim();
        if (!Set.of("COURT_DATE", "PRESCRIPTION_DEADLINE", "FILING_DEADLINE", "LIMITATION_PERIOD", "OTHER").contains(keyDateType)) {
            throw new IllegalArgumentException("Unknown key date type: " + keyDateType);
        }
        String rs = blankToNull(r.recessStart()), re = blankToNull(r.recessEnd());
        DeadlineCalculator.validate(ruleOf(r.amount(), r.unit().trim(), r.direction().trim(), r.rollover().trim(), rs, re));
        String code = r.code().trim().toUpperCase(Locale.ENGLISH);
        if (code.length() < 2 || code.length() > 60) throw new IllegalArgumentException("The code must be 2 to 60 characters");
        return new Clean(code, r.name().trim(), blankToNull(r.description()), blankToNull(r.court()), r.triggerLabel().trim(), keyDateType, r.amount(),
                r.unit().trim(), r.direction().trim(), r.rollover().trim(), rs, re, blankToNull(r.sourceReference()));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private LpDeadlineRule findRule(TenantId tenantId, UUID id) {
        return ruleRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpDeadlineRule", id.toString()));
    }

    private static final String COMPUTED = "Computed: ";

    private static String note(String ruleName, String triggerLabel, LocalDate trigger, String userNotes) {
        String line = COMPUTED + ruleName + " (" + triggerLabel + ": " + trigger + ")";
        return userNotes == null || userNotes.isBlank() ? line : line + "\n" + userNotes.trim();
    }

    /** Removes the first "Computed: ..." line so a correction replaces it instead of stacking another. */
    private static String stripComputedLine(String notes) {
        if (!notes.startsWith(COMPUTED)) return notes;
        int nl = notes.indexOf('\n');
        return nl < 0 ? null : notes.substring(nl + 1);
    }

    private static String dayName(LocalDate d) { return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH); }

    private static DeadlineRuleResponse toResponse(LpDeadlineRule r) {
        return new DeadlineRuleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.getCourt(), r.getTriggerLabel(), r.getKeyDateType(),
                r.getAmount(), r.getUnit(), r.getDirection(), r.getRollover(), r.getRecessStart(), r.getRecessEnd(), r.getSourceReference(), r.isActive());
    }

    private static DeadlineComputationResponse toResponse(LpDeadlineComputation c, boolean current, LpMatterKeyDate k) {
        List<String> lines = c.getExplanation().isBlank() ? List.of() : List.of(c.getExplanation().split("\n"));
        return new DeadlineComputationResponse(c.getId(), c.getMatterId(), c.getKeyDateId(), c.getSupersedesId(), current, c.getRuleCode(), c.getRuleName(),
                c.getAmount(), c.getUnit(), c.getDirection(), c.getRollover(), c.getRecessStart(), c.getRecessEnd(), c.getTriggerLabel(), c.getTriggerDate(),
                c.getDueDate(), lines, c.getReason(), c.getComputedByName(), c.getComputedAt(),
                k == null ? null : k.getDueDate(), k == null ? null : k.getStatus(), current && k != null && !k.getDueDate().equals(c.getDueDate()));
    }
}
