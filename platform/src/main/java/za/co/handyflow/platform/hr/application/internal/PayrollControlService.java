package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.model.PayrollExceptionRules.Item;
import za.co.handyflow.platform.hr.domain.model.PayrollExceptionRules.Line;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * The checks around a calculated pay run: what to look at, who may approve, finalise and lock it, and the history of each
 * step. The calculation itself stays in {@link PayrollService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollControlService {

    private final HrPayRunRepository payRunRepo;
    private final HrPayslipRepository payslipRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrPayRunEventRepository eventRepo;
    private final HrPayrollSettingsRepository settingsRepo;
    private final HrEmployeeChangeRepository changeRepo;
    private final MedicalSchemeService medicalSchemeService;
    private final OvertimeService overtimeService;
    private final JdbcTemplate jdbc;

    // ── Review ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PayRunReviewResponse review(TenantId tenantId, UUID runId) {
        HrPayRun run = load(tenantId, runId);
        Checked c = exceptionsFor(tenantId, run);
        return toReview(run, c.items(), c.comparedWith());
    }

    /** The exceptions found, and the number of the earlier run they were compared with (null when there was none). */
    private record Checked(List<Item> items, String comparedWith) {}

    private PayRunReviewResponse toReview(HrPayRun run, List<Item> items, String comparedWith) {
        long errors = items.stream().filter(i -> i.severity() == PayrollExceptionRules.Severity.ERROR).count();
        return new PayRunReviewResponse(run.getId(), run.getPayRunNumber(), run.getStatus(), comparedWith, errors > 0,
                (int) errors, items.size() - (int) errors,
                items.stream().map(i -> new PayRunReviewResponse.Item(i.severity().name(), i.code(), i.employee(), i.message())).toList());
    }

    private Checked exceptionsFor(TenantId tenantId, HrPayRun run) {
        List<HrPayslip> slips = payslipRepo.findByPayRun(run.getId());
        Map<UUID, HrEmployee> people = peopleOf(tenantId, slips.stream().map(HrPayslip::getEmployeeId).toList());
        Map<UUID, MedicalSchemeRules.Members> members = medicalSchemeService.membersOn(tenantId, people.values(), run.getPayDate());

        List<Line> lines = new ArrayList<>();
        for (HrPayslip s : slips) {
            HrEmployee e = people.get(s.getEmployeeId());
            String name = e != null ? e.getFullName() : "Unknown employee";
            String number = e != null ? e.getEmployeeNumber() : "—";
            boolean assumed = e != null && members.get(e.getId()) != null && members.get(e.getId()).assumed();
            boolean noBank = e == null || e.getBankAccountNumber() == null || e.getBankAccountNumber().isBlank();
            lines.add(new Line(s.getEmployeeId(), name, number, s.getNetPay(), assumed, noBank));
        }

        Map<UUID, BigDecimal> prevNet = null;
        Map<UUID, String> prevNames = new HashMap<>();
        String comparedWith = null;
        List<HrPayRun> prev = payRunRepo.previousIssued(tenantId, run.getPeriodStart(), PageRequest.of(0, 1));
        if (!prev.isEmpty()) {
            comparedWith = prev.get(0).getPayRunNumber();
            prevNet = new HashMap<>();
            List<HrPayslip> prevSlips = payslipRepo.findByPayRun(prev.get(0).getId());
            Map<UUID, HrEmployee> prevPeople = peopleOf(tenantId, prevSlips.stream().map(HrPayslip::getEmployeeId).toList());
            for (HrPayslip s : prevSlips) {
                prevNet.put(s.getEmployeeId(), s.getNetPay());
                HrEmployee e = prevPeople.get(s.getEmployeeId());
                prevNames.put(s.getEmployeeId(), e != null ? e.getFullName() + " (" + e.getEmployeeNumber() + ")" : s.getEmployeeId().toString());
            }
        }
        List<Item> items = new ArrayList<>(PayrollExceptionRules.check(lines, prevNet, prevNames));
        // Pay or bank details that changed after this run was calculated mean the figures are out of date.
        if (run.getProcessedAt() != null && !slips.isEmpty()) {
            Set<UUID> changed = new LinkedHashSet<>();
            for (HrEmployeeChange c : changeRepo.appliedSince(tenantId, people.keySet(), run.getProcessedAt())) changed.add(c.getEmployeeId());
            for (UUID id : changed) {
                HrEmployee e = people.get(id);
                items.add(new Item(PayrollExceptionRules.Severity.WARNING, "PAY_CHANGED_AFTER_CALCULATION",
                        e != null ? e.getFullName() + " (" + e.getEmployeeNumber() + ")" : id.toString(),
                        "Pay or bank details changed after this run was calculated. Send the run back and calculate it again if the change should apply."));
            }
        }
        return new Checked(items, comparedWith);
    }

    private Map<UUID, HrEmployee> peopleOf(TenantId tenantId, Collection<UUID> ids) {
        Map<UUID, HrEmployee> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        employeeRepo.findAllById(ids).stream()
                .filter(e -> tenantId.getValue().equals(e.getTenantId()))
                .forEach(e -> out.put(e.getId(), e));
        return out;
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    @Transactional
    public PayRunResponse approve(TenantId tenantId, UUID runId, UUID actor, String note) {
        HrPayRun run = load(tenantId, runId);
        boolean allowSelf = allowSelfApproval(tenantId);
        boolean blocking = PayrollExceptionRules.hasBlocking(exceptionsFor(tenantId, run).items());
        String from = run.getStatus();
        boolean self = PayRunWorkflowRules.checkCanApprove(from, run.getPreparedBy(), actor, allowSelf, blocking);
        run.approve(actor);
        payRunRepo.save(run);
        eventRepo.save(HrPayRunEvent.of(tenantId.getValue(), runId, self ? "APPROVED_OWN_RUN" : "APPROVED", from, run.getStatus(), actor,
                self ? "Approved by the person who calculated it, allowed by this business's payroll setting. " + nz(note) : note));
        log.info("Pay run {} approved by {} self={}", run.getPayRunNumber(), actor, self);
        return toResponse(run);
    }

    @Transactional
    public PayRunResponse sendBack(TenantId tenantId, UUID runId, UUID actor, String note) {
        HrPayRun run = load(tenantId, runId);
        String from = run.getStatus();
        PayRunWorkflowRules.checkCanReject(from, note);
        run.sendBack();
        payRunRepo.save(run);
        eventRepo.save(HrPayRunEvent.of(tenantId.getValue(), runId, "SENT_BACK", from, run.getStatus(), actor, note));
        log.info("Pay run {} sent back by {}", run.getPayRunNumber(), actor);
        return toResponse(run);
    }

    /** Cancels a draft run that will not be paid. Its overtime goes back so the next run can pay it. */
    @Transactional
    public PayRunResponse cancel(TenantId tenantId, UUID runId, UUID actor, String reason) {
        HrPayRun run = load(tenantId, runId);
        String from = run.getStatus();
        PayRunWorkflowRules.checkCanCancel(from, reason);
        requireActor(actor);
        run.cancel();
        payRunRepo.save(run);
        int released = overtimeService.releaseFor(tenantId, runId);
        eventRepo.save(HrPayRunEvent.of(tenantId.getValue(), runId, "CANCELLED", from, run.getStatus(), actor,
                reason.trim() + (released > 0 ? " (" + released + " overtime claim(s) released)" : "")));
        log.info("Pay run {} cancelled by {}, {} overtime claim(s) released", run.getPayRunNumber(), actor, released);
        return toResponse(run);
    }

    @Transactional
    public PayRunResponse finalise(TenantId tenantId, UUID runId, UUID actor, String note) {
        HrPayRun run = load(tenantId, runId);
        String from = run.getStatus();
        boolean self = PayRunWorkflowRules.checkCanFinalise(from, run.getPreparedBy(), actor, allowSelfApproval(tenantId));
        run.finalise(actor);
        payRunRepo.save(run);
        eventRepo.save(HrPayRunEvent.of(tenantId.getValue(), runId, self ? "FINALISED_OWN_RUN" : "FINALISED", from, run.getStatus(), actor, note));
        log.info("Pay run {} finalised by {}", run.getPayRunNumber(), actor);
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public List<PayRunEventResponse> events(TenantId tenantId, UUID runId) {
        load(tenantId, runId);
        return eventRepo.forPayRun(tenantId, runId).stream()
                .map(e -> new PayRunEventResponse(e.getId(), e.getAction(), e.getFromStatus(), e.getToStatus(), e.getActorId(), e.getNote(), e.getCreatedAt()))
                .toList();
    }

    // ── Settings and access review ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PayrollSettingsResponse settings(TenantId tenantId) {
        HrPayrollSettings s = settingsRepo.findById(tenantId.getValue()).orElse(null);
        return new PayrollSettingsResponse(s != null && s.isAllowSelfApproval(), s != null ? s.getUpdatedAt() : null,
                PayrollAccess.LEGACY_ACCESS_ENDS, PayrollAccess.legacyOpen(LocalDate.now()));
    }

    @Transactional
    public PayrollSettingsResponse updateSettings(TenantId tenantId, boolean allowSelfApproval, UUID actor) {
        HrPayrollSettings s = settingsRepo.findById(tenantId.getValue()).orElseGet(() -> HrPayrollSettings.create(tenantId.getValue()));
        s.setAllowSelfApproval(allowSelfApproval, actor);
        settingsRepo.save(s);
        log.info("Payroll self-approval set to {} for tenant {} by {}", allowSelfApproval, tenantId.getValue(), actor);
        return settings(tenantId);
    }

    @Transactional(readOnly = true)
    public PayrollAccessReviewResponse accessReview(TenantId tenantId) {
        List<PayrollAccessReviewResponse.Role> roles = jdbc.query("""
                SELECT role_id, role_name, users_in_role, legacy_held, granted, needs_review, note
                FROM hr_payroll_access_review WHERE tenant_id = ? ORDER BY needs_review DESC, role_name""",
                (rs, i) -> new PayrollAccessReviewResponse.Role((UUID) rs.getObject("role_id"), rs.getString("role_name"),
                        rs.getInt("users_in_role"), rs.getString("legacy_held"), rs.getString("granted"),
                        rs.getBoolean("needs_review"), rs.getString("note")),
                tenantId.getValue());
        return new PayrollAccessReviewResponse(PayrollAccess.LEGACY_ACCESS_ENDS, (int) roles.stream().filter(PayrollAccessReviewResponse.Role::needsReview).count(), roles);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean allowSelfApproval(TenantId tenantId) {
        return settingsRepo.findById(tenantId.getValue()).map(HrPayrollSettings::isAllowSelfApproval).orElse(false);
    }

    private HrPayRun load(TenantId tenantId, UUID runId) {
        return payRunRepo.findByTenantAndId(tenantId, runId)
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", runId.toString()));
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private PayRunResponse toResponse(HrPayRun r) {
        return new PayRunResponse(r.getId(), r.getPayRunNumber(), r.getPeriodStart(), r.getPeriodEnd(), r.getPayDate(),
                r.getTaxYear(), r.getStatus(), r.getTotalGross(), r.getTotalPaye(), r.getTotalUif(), r.getTotalSdl(), r.getTotalNet(),
                r.getEmployeeCount(), r.getNotes(), r.getProcessedAt(), r.getCreatedAt(),
                r.getPreparedBy(), r.getPreparedAt(), r.getApprovedBy(), r.getApprovedAt(), r.getFinalisedBy(), r.getFinalisedAt(), List.of());
    }
}
