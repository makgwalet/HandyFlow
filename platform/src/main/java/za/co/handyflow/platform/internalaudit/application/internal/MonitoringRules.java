package za.co.handyflow.platform.internalaudit.application.internal;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The patterns continuous monitoring looks for. Pure functions over plain records, so they can be checked without a database. Everything found is a
 * <em>candidate for a person to look at</em>: none of these is a conclusion that anything is wrong.
 */
public final class MonitoringRules {
    private MonitoringRules() {}
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    public record Params(BigDecimal threshold, int backdatedDays, int duplicateWindowDays) {
        public static Params defaults() { return new Params(new BigDecimal("50000"), 30, 3); }
    }
    public record JournalIn(UUID id, String number, String status, LocalDate entryDate, Instant postedAt, String description, String reference, String entryType,
                            String sourceKey, BigDecimal total, UUID createdBy, UUID postedBy, UUID reverses) {
        boolean live() { return "POSTED".equals(status) && reverses == null; }
        boolean manual() { return "MANUAL".equals(entryType) && sourceKey == null && live(); }
    }
    public record BankIn(UUID id, UUID account, LocalDate date, String type, BigDecimal amount, String description, String reference) {}
    public record UserIn(UUID id, String name, boolean active, Set<String> permissions) {}
    public record Candidate(String rule, String severity, String subjectType, List<UUID> subjectIds, List<UUID> involved, String fingerprint, String summary, String detail, BigDecimal amount, LocalDate occurredOn) {}

    /** Pairs of permissions one person should not hold together: doing both lets them complete a process alone. */
    public record Conflict(String code, String a, String b, String severity, String meaning) {}
    public static final List<Conflict> CONFLICTS = List.of(
        new Conflict("AP_CAPTURE_APPROVE", "AP_MANAGE", "AP_APPROVE", "MEDIUM", "can capture and approve supplier bills"),
        new Conflict("AP_APPROVE_PAY", "AP_APPROVE", "AP_PAY", "HIGH", "can approve and pay supplier bills"),
        new Conflict("AP_CAPTURE_PAY", "AP_MANAGE", "AP_PAY", "HIGH", "can capture and pay supplier bills"),
        new Conflict("PAYROLL_HR", "HR_MANAGE", "PAYROLL_RUN", "HIGH", "can change employee records and run payroll"),
        new Conflict("INVOICE_CREDIT", "INVOICE_CREATE", "CREDIT_NOTE_APPROVE", "MEDIUM", "can raise invoices and approve credit notes"),
        new Conflict("LEDGER_AUDIT", "ACCOUNTING_MANAGE", "AUDIT_MANAGE", "MEDIUM", "can work in the ledger and audit it (independence)"));

    public static List<Candidate> run(Params p, List<JournalIn> journals, List<BankIn> bank, List<UserIn> users) {
        List<Candidate> out = new ArrayList<>();
        out.addAll(duplicateJournals(journals));
        out.addAll(duplicateBankPayments(bank, p.duplicateWindowDays()));
        out.addAll(manualJournalPatterns(p, journals));
        out.addAll(segregationConflicts(users));
        return out;
    }

    static String norm(String s) { return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", ""); }
    static String ids(List<UUID> l) { return l.stream().sorted().map(UUID::toString).collect(Collectors.joining("+")); }
    static String money(BigDecimal b) { return "R" + b.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }

    /** Two or more live journals on the same date for the same amount with the same reference (or, with none, the same description). */
    static List<Candidate> duplicateJournals(List<JournalIn> journals) {
        Map<String, List<JournalIn>> groups = new LinkedHashMap<>();
        for (JournalIn j : journals) {
            if (!j.live() || j.total() == null || j.total().signum() <= 0) continue;
            String key = !norm(j.reference()).isEmpty() ? norm(j.reference()) : norm(j.description());
            if (key.isEmpty()) continue;
            groups.computeIfAbsent(j.entryDate() + "|" + j.total().stripTrailingZeros().toPlainString() + "|" + key, k -> new ArrayList<>()).add(j);
        }
        List<Candidate> out = new ArrayList<>();
        for (List<JournalIn> g : groups.values()) {
            if (g.size() < 2) continue;
            List<UUID> idList = g.stream().map(JournalIn::id).toList();
            List<UUID> people = g.stream().flatMap(j -> java.util.stream.Stream.of(j.createdBy(), j.postedBy())).filter(Objects::nonNull).distinct().toList();
            out.add(new Candidate("DUPLICATE_JOURNAL", "MEDIUM", "JOURNAL", idList, people, "DUPLICATE_JOURNAL:" + ids(idList),
                    g.size() + " journals for " + money(g.get(0).total()) + " on " + g.get(0).entryDate() + " look the same",
                    "Journals " + g.stream().map(JournalIn::number).collect(Collectors.joining(", ")) + " share the date, amount and reference or description.", g.get(0).total(), g.get(0).entryDate()));
        }
        return out;
    }

    /** Payments out of the same bank account for the same amount, with the same reference or description, within a few days of each other. */
    static List<Candidate> duplicateBankPayments(List<BankIn> bank, int windowDays) {
        Map<String, List<BankIn>> groups = new LinkedHashMap<>();
        for (BankIn b : bank) {
            if (!"DEBIT".equals(b.type()) || b.amount() == null || b.amount().signum() <= 0) continue;
            String key = !norm(b.reference()).isEmpty() ? norm(b.reference()) : norm(b.description());
            if (key.isEmpty()) continue;
            groups.computeIfAbsent(b.account() + "|" + b.amount().stripTrailingZeros().toPlainString() + "|" + key, k -> new ArrayList<>()).add(b);
        }
        List<Candidate> out = new ArrayList<>();
        for (List<BankIn> g : groups.values()) {
            g.sort(Comparator.comparing(BankIn::date).thenComparing(BankIn::id));
            List<BankIn> cluster = new ArrayList<>();
            for (BankIn b : g) {
                if (!cluster.isEmpty() && b.date().isAfter(cluster.get(cluster.size() - 1).date().plusDays(windowDays))) { flush(cluster, out); cluster = new ArrayList<>(); }
                cluster.add(b);
            }
            flush(cluster, out);
        }
        return out;
    }

    private static void flush(List<BankIn> cluster, List<Candidate> out) {
        if (cluster.size() < 2) return;
        List<UUID> idList = cluster.stream().map(BankIn::id).toList();
        BankIn first = cluster.get(0);
        out.add(new Candidate("DUPLICATE_BANK_PAYMENT", "HIGH", "BANK_TRANSACTION", idList, List.of(), "DUPLICATE_BANK_PAYMENT:" + ids(idList),
                cluster.size() + " payments of " + money(first.amount()) + " went out between " + first.date() + " and " + cluster.get(cluster.size() - 1).date() + " with the same reference",
                "Reference or description: " + (first.reference() != null && !first.reference().isBlank() ? first.reference() : first.description()), first.amount(), first.date()));
    }

    /** Patterns on journals people made by hand. */
    static List<Candidate> manualJournalPatterns(Params p, List<JournalIn> journals) {
        List<Candidate> out = new ArrayList<>();
        for (JournalIn j : journals) {
            if (!j.live() || j.total() == null) continue;
            boolean big = j.total().compareTo(p.threshold()) >= 0;
            List<UUID> one = List.of(j.id());
            List<UUID> people = java.util.stream.Stream.of(j.createdBy(), j.postedBy()).filter(Objects::nonNull).distinct().toList();
            // The same person made and posted a journal: the system is meant to stop this, so seeing it means a control did not work.
            if (j.createdBy() != null && j.createdBy().equals(j.postedBy()))
                out.add(new Candidate("SAME_MAKER_AND_POSTER", "HIGH", "JOURNAL", one, people, "SAME_MAKER_AND_POSTER:" + j.id(),
                        "Journal " + j.number() + " was made and posted by the same person", "Amount " + money(j.total()) + ".", j.total(), j.entryDate()));
            if (!j.manual()) continue;
            if (big && j.total().remainder(new BigDecimal("1000")).signum() == 0)
                out.add(new Candidate("ROUND_MANUAL_JOURNAL", "MEDIUM", "JOURNAL", one, people, "ROUND_MANUAL_JOURNAL:" + j.id(),
                        "Manual journal " + j.number() + " is a round " + money(j.total()), "Large manual journals for round amounts are sometimes estimates or plugs.", j.total(), j.entryDate()));
            if (big && j.postedAt() != null) {
                DayOfWeek d = j.postedAt().atZone(SAST).getDayOfWeek();
                if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY)
                    out.add(new Candidate("WEEKEND_POSTING", "LOW", "JOURNAL", one, people, "WEEKEND_POSTING:" + j.id(),
                            "Manual journal " + j.number() + " for " + money(j.total()) + " was posted on a " + (d == DayOfWeek.SATURDAY ? "Saturday" : "Sunday"), null, j.total(), j.entryDate()));
            }
            if (j.postedAt() != null && j.entryDate() != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(j.entryDate(), j.postedAt().atZone(SAST).toLocalDate());
                if (days > p.backdatedDays())
                    out.add(new Candidate("BACKDATED_JOURNAL", big ? "HIGH" : "MEDIUM", "JOURNAL", one, people, "BACKDATED_JOURNAL:" + j.id(),
                            "Manual journal " + j.number() + " was posted " + days + " days after its date", "Dated " + j.entryDate() + ", posted " + j.postedAt().atZone(SAST).toLocalDate() + ", " + money(j.total()) + ".", j.total(), j.entryDate()));
            }
            if (big && norm(j.description()).isEmpty() && norm(j.reference()).isEmpty())
                out.add(new Candidate("MISSING_EXPLANATION", "MEDIUM", "JOURNAL", one, people, "MISSING_EXPLANATION:" + j.id(),
                        "Manual journal " + j.number() + " for " + money(j.total()) + " has no description or reference", null, j.total(), j.entryDate()));
        }
        return out;
    }

    /** Active people who hold both halves of a conflicting pair. The holder may be an administrator whose role simply includes everything. */
    static List<Candidate> segregationConflicts(List<UserIn> users) {
        List<Candidate> out = new ArrayList<>();
        for (UserIn u : users) {
            if (!u.active()) continue;
            for (Conflict c : CONFLICTS)
                if (u.permissions().contains(c.a()) && u.permissions().contains(c.b()))
                    out.add(new Candidate("SOD_CONFLICT", c.severity(), "USER", List.of(u.id()), List.of(u.id()), "SOD_CONFLICT:" + u.id() + ":" + c.code(),
                            u.name() + " " + c.meaning(), "Holds both " + c.a() + " and " + c.b() + ". Often caused by a role that includes everything; the question is whether a compensating control exists.", null, null));
        }
        return out;
    }
}
