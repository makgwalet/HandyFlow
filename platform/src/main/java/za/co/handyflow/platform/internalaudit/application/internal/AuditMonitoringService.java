package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.domain.repository.AccBankAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankTransactionRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccJournalEntryRepository;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.domain.model.Permission;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.MonitoringRules.*;
import za.co.handyflow.platform.internalaudit.domain.model.AuditMonitorAlert;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditMonitorAlertRepository;
import za.co.handyflow.platform.internalaudit.dto.AuditMonitoringDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Continuous controls monitoring. A scan reads journals, bank payments and people's permissions, runs {@link MonitoringRules} and files an alert for
 * each pattern not seen before. Alerts are for human review: nothing here changes a ledger entry, closes a finding, or reaches a conclusion.
 */
@Service
@RequiredArgsConstructor
public class AuditMonitoringService {
    public record Actor(UUID userId, String name) {}
    static final String NOTICE = "These are patterns that deserve a look, not findings. Many have an innocent explanation. Only a person can close an alert, and not someone it is about.";
    static final int DEFAULT_LOOKBACK = 90;

    private final AuditMonitorAlertRepository alerts;
    private final AccJournalEntryRepository journals;
    private final AccBankAccountRepository bankAccounts;
    private final AccBankTransactionRepository bankTransactions;
    private final UserRepository users;
    private final AuditFindingRepository findings;
    private final AuditTrailFacade trail;

    @Transactional(readOnly = true)
    public Alerts list(TenantId tenant) {
        var all = alerts.findByTenantIdOrderByFirstSeenDesc(tenant.getValue());
        return new Alerts(counts(all), all.stream().map(this::view).toList(), NOTICE);
    }

    /** Runs the rules over the last {@code lookbackDays} days. Safe to run any number of times: a pattern already filed is only marked as seen again. */
    @Transactional
    public ScanResult scan(TenantId tenant, LocalDate today, ScanRequest req) {
        Params defaults = Params.defaults();
        Params p = new Params(req != null && req.threshold() != null && req.threshold().signum() > 0 ? req.threshold() : defaults.threshold(),
                req != null && req.backdatedDays() != null && req.backdatedDays() > 0 ? req.backdatedDays() : defaults.backdatedDays(),
                req != null && req.duplicateWindowDays() != null && req.duplicateWindowDays() >= 0 ? req.duplicateWindowDays() : defaults.duplicateWindowDays());
        int lookback = req != null && req.lookbackDays() != null && req.lookbackDays() > 0 && req.lookbackDays() <= 400 ? req.lookbackDays() : DEFAULT_LOOKBACK;
        LocalDate from = today.minusDays(lookback);

        var js = journals.findPostedInRange(tenant, from, today).stream().map(e -> new JournalIn(e.getId(), e.getEntryNumber(), e.getStatus(), e.getEntryDate(), e.getPostedAt(),
                e.getDescription(), e.getReference(), e.getEntryType(), e.getSourceKey(), e.getTotalDebit(), e.getCreatedBy(), e.getPostedBy(), e.getReversesEntryId())).toList();
        List<BankIn> bank = new ArrayList<>();
        for (var acc : bankAccounts.findAllActive(tenant))
            for (var t : bankTransactions.findByBankAccount(tenant, acc.getId(), PageRequest.of(0, 2000)).getContent())
                if (t.getTransactionDate() != null && !t.getTransactionDate().isBefore(from)) bank.add(new BankIn(t.getId(), acc.getId(), t.getTransactionDate(), t.getTransactionType(), t.getAmount(), t.getDescription(), t.getReference()));
        var people = users.findByTenantIdOrderByLastNameAscFirstNameAsc(tenant).stream().map(u -> new UserIn(u.getId(), (u.getFirstName() + " " + u.getLastName()).trim(), u.isActive(),
                u.getRoles().stream().flatMap(r -> r.getPermissions().stream()).map(Permission::getName).collect(Collectors.toSet()))).toList();

        var found = MonitoringRules.run(p, js, bank, people);
        Map<String, AuditMonitorAlert> existing = new HashMap<>();
        if (!found.isEmpty()) alerts.findByTenantIdAndFingerprintIn(tenant.getValue(), found.stream().map(Candidate::fingerprint).toList()).forEach(a -> existing.put(a.getFingerprint(), a));
        int fresh = 0, again = 0;
        Map<String, Integer> byRule = new TreeMap<>();
        for (Candidate c : found) {
            var have = existing.get(c.fingerprint());
            if (have != null) { have.seenAgain(); alerts.save(have); again++; continue; }
            alerts.save(AuditMonitorAlert.open(tenant.getValue(), c.rule(), c.severity(), c.subjectType(), c.subjectIds().stream().map(UUID::toString).collect(Collectors.joining(",")),
                    c.involved().stream().map(UUID::toString).collect(Collectors.joining(",")), c.fingerprint(), c.summary(), c.detail(), c.amount(), c.occurredOn()));
            byRule.merge(c.rule(), 1, Integer::sum);
            fresh++;
        }
        if (fresh > 0) trail.record(tenant, "AUDIT_MONITOR_SCAN", "AUDIT_MONITOR", tenant.getValue(), null, Map.of("newAlerts", fresh, "byRule", byRule.toString()));
        return new ScanResult(found.size(), fresh, again, byRule, js.size(), bank.size(), people.size());
    }

    @Transactional
    public Alerts review(TenantId tenant, Actor actor, UUID id, String status, String note, UUID findingId) {
        UUID t = tenant.getValue();
        var a = alerts.findByTenantIdAndId(t, id).orElseThrow(() -> new ResourceNotFoundException("AuditMonitorAlert", id.toString()));
        if (findingId != null && findings.findByTenantAndId(t, findingId).isEmpty()) throw bad("That finding does not exist", "MON_INVALID");
        String before = a.getStatus();
        guard(() -> a.review(status, note, findingId, actor.userId(), actor.name()));
        alerts.save(a);
        trail.record(tenant, "AUDIT_MONITOR_REVIEWED", "AUDIT_MONITOR_ALERT", id, Map.of("status", before), Map.of("status", a.getStatus(), "rule", a.getRule()));
        return list(tenant);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────

    private static Counts counts(List<AuditMonitorAlert> all) {
        int open = 0, h = 0, m = 0, l = 0, ir = 0, d = 0, e = 0;
        for (var a : all) {
            switch (a.getStatus()) { case "IN_REVIEW" -> ir++; case "DISMISSED" -> d++; case "ESCALATED" -> e++; default -> { } }
            if (!a.isClosed()) {
                open++;
                switch (a.getSeverity()) { case "HIGH" -> h++; case "MEDIUM" -> m++; default -> l++; }
            }
        }
        return new Counts(open, h, m, l, ir, d, e);
    }

    private AlertView view(AuditMonitorAlert a) {
        return new AlertView(a.getId(), a.getRule(), a.getSeverity(), a.getSubjectType(), Arrays.asList(a.getSubjectIds().split(",")), a.getSummary(), a.getDetail(), a.getAmount(), a.getOccurredOn(),
                a.getStatus(), a.getFirstSeen(), a.getLastSeen(), a.getReviewedByName(), a.getReviewedAt(), a.getDisposition(), a.getFindingId());
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
    private static void guard(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException e) { throw bad(e.getMessage(), "MON_INVALID"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "MON_STATE"); }
    }
}
