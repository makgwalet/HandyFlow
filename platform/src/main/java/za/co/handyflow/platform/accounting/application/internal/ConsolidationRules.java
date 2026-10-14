package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Pure rules for group consolidation (finance F26). No framework, no database.
 * Balances come in as "net" figures (debits minus credits) per member company and account code. They are mapped to group
 * lines, intercompany pairs are eliminated, and everything is shown per company plus an Elimination column and a
 * Consolidated column. Every figure is a sum of balanced journals, so the consolidated trial balance always nets to zero.
 */
public final class ConsolidationRules {
    private ConsolidationRules() {}

    public static final List<String> TYPE_ORDER = List.of("INCOME", "EXPENSE", "ASSET", "LIABILITY", "EQUITY", "DIFFERENCE");
    public static final String DIFFERENCE_CODE = "IC-DIFF";

    public record Bal(UUID tenantId, String code, String name, String type, BigDecimal net) {}
    public record MapTo(String groupCode, String groupName) {}
    public record Key(UUID tenantId, String code) {}
    public record Elim(String name, UUID tenantA, String codeA, UUID tenantB, String codeB) {}

    /** amounts line up with the entity order; all in natural sign (assets, expenses: debit positive; the rest: credit positive). */
    public record Row(String code, String name, String type, List<BigDecimal> amounts, BigDecimal elimination, BigDecimal consolidated) {}
    public record IcCheck(String name, BigDecimal sideA, BigDecimal sideB, BigDecimal difference) {}
    public record Result(List<Row> rows, List<IcCheck> checks, BigDecimal income, BigDecimal expenses, BigDecimal profit,
                         BigDecimal assets, BigDecimal liabilities, BigDecimal equity, BigDecimal balanceDifference) {}

    static boolean debitNatural(String type) { return "ASSET".equals(type) || "EXPENSE".equals(type) || "DIFFERENCE".equals(type); }
    static BigDecimal natural(String type, BigDecimal net) { return debitNatural(type) ? net : net.negate(); }

    public static Result consolidate(List<UUID> entities, List<Bal> balances, Map<Key, MapTo> mapping, List<Elim> eliminations) {
        // line key -> per-entity net, elimination net
        Map<String, Line> lines = new LinkedHashMap<>();
        for (Bal b : balances) {
            if (b.net() == null || b.net().signum() == 0) continue;
            int idx = entities.indexOf(b.tenantId());
            if (idx < 0) continue;
            MapTo m = mapping.get(new Key(b.tenantId(), b.code()));
            String code = m != null ? m.groupCode() : b.code();
            String name = m != null && m.groupName() != null && !m.groupName().isBlank() ? m.groupName() : b.name();
            Line l = lines.computeIfAbsent(b.type() + "|" + code, k -> new Line(code, name, b.type(), entities.size()));
            l.net[idx] = l.net[idx].add(b.net());
        }
        List<IcCheck> checks = new ArrayList<>();
        BigDecimal diffTotal = BigDecimal.ZERO;
        Line diff = new Line(DIFFERENCE_CODE, "Unreconciled intercompany difference", "DIFFERENCE", entities.size());
        for (Elim e : eliminations) {
            BigDecimal a = remove(lines, entities, mapping, e.tenantA(), e.codeA());
            BigDecimal b = remove(lines, entities, mapping, e.tenantB(), e.codeB());
            BigDecimal d = a.add(b);
            checks.add(new IcCheck(e.name(), a, b, d));
            diffTotal = diffTotal.add(d);
        }
        if (diffTotal.signum() != 0) { diff.elim = diffTotal; lines.put("DIFFERENCE|" + DIFFERENCE_CODE, diff); }

        List<Row> rows = new ArrayList<>();
        for (Line l : lines.values()) {
            List<BigDecimal> amounts = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal n : l.net) { amounts.add(natural(l.type, n)); sum = sum.add(n); }
            BigDecimal consolidatedNet = sum.add(l.elim);
            rows.add(new Row(l.code, l.name, l.type, amounts, natural(l.type, l.elim), natural(l.type, consolidatedNet)));
        }
        rows.sort(Comparator.comparingInt((Row r) -> TYPE_ORDER.indexOf(r.type())).thenComparing(Row::code));

        BigDecimal income = total(rows, "INCOME"), expenses = total(rows, "EXPENSE"), assets = total(rows, "ASSET");
        BigDecimal liabilities = total(rows, "LIABILITY"), equity = total(rows, "EQUITY"), difference = total(rows, "DIFFERENCE");
        BigDecimal profit = income.subtract(expenses);
        // Assets = liabilities + equity + profit for the period, with the intercompany difference (a debit) sitting on the asset side.
        BigDecimal balanceDifference = assets.add(difference).subtract(liabilities).subtract(equity).subtract(profit);
        return new Result(rows, checks, income, expenses, profit, assets, liabilities, equity, balanceDifference);
    }

    private static BigDecimal total(List<Row> rows, String type) {
        return rows.stream().filter(r -> type.equals(r.type())).map(Row::consolidated).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Takes an account's whole balance out of the consolidation. Returns the net removed (before sign flip). */
    private static BigDecimal remove(Map<String, Line> lines, List<UUID> entities, Map<Key, MapTo> mapping, UUID tenant, String code) {
        int idx = entities.indexOf(tenant);
        if (idx < 0) return BigDecimal.ZERO;
        MapTo m = mapping.get(new Key(tenant, code));
        String groupCode = m != null ? m.groupCode() : code;
        BigDecimal removed = BigDecimal.ZERO;
        for (Line l : lines.values()) {
            if (!l.code.equals(groupCode)) continue;
            // Only the member's own amount on the line is removed, not other members' balances on the same group line.
            BigDecimal mine = l.net[idx];
            if (mine.signum() == 0) continue;
            l.elim = l.elim.subtract(mine);
            removed = removed.add(mine);
        }
        return removed;
    }

    private static final class Line {
        final String code, name, type; final BigDecimal[] net; BigDecimal elim = BigDecimal.ZERO;
        Line(String code, String name, String type, int n) {
            this.code = code; this.name = name; this.type = type; this.net = new BigDecimal[n];
            Arrays.fill(net, BigDecimal.ZERO);
        }
    }

    // ── validation ───────────────────────────────────────────────────────────

    public static String ownershipProblem(BigDecimal percent) {
        if (percent == null || percent.signum() <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) return "Ownership must be more than 0 and at most 100 percent.";
        return null;
    }

    public static String groupNameProblem(String name) {
        if (name == null || name.isBlank()) return "Give the group a name.";
        if (name.trim().length() > 120) return "The name is too long (120 characters at most).";
        return null;
    }

    public static String mappingProblem(String accountCode, String groupCode) {
        if (accountCode == null || accountCode.isBlank()) return "Choose the account to map.";
        if (groupCode == null || groupCode.isBlank()) return "Give the group line a code.";
        if (groupCode.trim().length() > 20 || !groupCode.trim().matches("[A-Za-z0-9._-]+")) return "A group code uses letters, digits, dots, dashes and underscores (20 at most).";
        if (DIFFERENCE_CODE.equalsIgnoreCase(groupCode.trim())) return "That code is reserved for the intercompany difference.";
        return null;
    }

    /** A rule pairs one account in one company with one in another. An account may be in only one active rule. */
    public static String eliminationProblem(String name, UUID tenantA, String codeA, UUID tenantB, String codeB, Collection<UUID> members, List<Elim> existing) {
        if (name == null || name.isBlank()) return "Name the rule, for example Loan from Head Office.";
        if (tenantA == null || tenantB == null || codeA == null || codeB == null || codeA.isBlank() || codeB.isBlank()) return "Choose both companies and both accounts.";
        if (tenantA.equals(tenantB)) return "An intercompany rule needs two different companies.";
        if (!members.contains(tenantA) || !members.contains(tenantB)) return "Both companies must be members of the group.";
        for (Elim e : existing) {
            if (inUse(e, tenantA, codeA) || inUse(e, tenantB, codeB)) return "One of those accounts is already in another rule.";
        }
        return null;
    }

    private static boolean inUse(Elim e, UUID tenant, String code) {
        return (e.tenantA().equals(tenant) && e.codeA().equals(code)) || (e.tenantB().equals(tenant) && e.codeB().equals(code));
    }

    public static String inviteProblem(Instant expiresAt, Instant usedAt, Instant now) {
        if (usedAt != null) return "That invitation has already been used.";
        if (expiresAt == null || !expiresAt.isAfter(now)) return "That invitation has expired. Ask for a new one.";
        return null;
    }

    public static String joinProblem(UUID groupParent, UUID joiningTenant, boolean alreadyInAGroup) {
        if (groupParent.equals(joiningTenant)) return "This company already owns the group.";
        if (alreadyInAGroup) return "This company is already in a group. Leave it first.";
        return null;
    }

    /** Companies owned for less than 100 percent: full consolidation shows them at 100 and does not compute the minority share. */
    public static List<UUID> partlyOwned(Map<UUID, BigDecimal> ownership) {
        List<UUID> out = new ArrayList<>();
        ownership.forEach((t, p) -> { if (p.compareTo(BigDecimal.valueOf(100)) < 0) out.add(t); });
        return out;
    }
}
