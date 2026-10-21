package za.co.handyflow.platform.crm.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.LeadSource;

/**
 * Merging two customer records into one. The record that is kept ("keep") stays; the other ("other") is folded into it and removed.
 * The kept record's values win, a blank one is filled from the other, and the person may choose the other's value for any field where both differ.
 * Pure: no database, so every rule can be tested alone.
 */
final class MergeRules {
    private MergeRules() {}

    /** The fields of a record that merging can fill or choose between. */
    record Snap(String name, String email, String phone, String taxNumber, String notes, Map<String, String> address, UUID ownerId, LeadSource source,
                BigDecimal dealValue, LocalDate closeDate, Map<String, String> custom) {}

    /** A field where both records have a value and the values differ. keep and other are how each reads; owner is shown as an id. */
    record Conflict(String field, String label, String keep, String other) {}

    static final List<String> FIELDS = List.of("name", "email", "phone", "taxNumber", "address", "owner", "source", "dealValue", "closeDate");
    private static final Map<String, String> LABELS = Map.of("name", "Name", "email", "Email", "phone", "Phone", "taxNumber", "Tax number", "address", "Address",
            "owner", "Owner", "source", "Lead source", "dealValue", "Deal value", "closeDate", "Expected close date");
    static final String CUSTOM = "custom.";
    private static final List<String> ADDRESS_ORDER = List.of("street", "suburb", "city", "province", "postalCode");

    static void check(UUID keep, UUID other, CustomerType keepType, CustomerType otherType) {
        if (keep == null || other == null || keep.equals(other)) throw new IllegalArgumentException("Choose two different customers");
        if (otherType == CustomerType.CUSTOMER && keepType == CustomerType.LEAD) {
            throw new IllegalArgumentException("Keep the customer and merge the lead into it, not the other way round");
        }
    }

    /** Records in other parts of the system that belong to the record being removed. Merging does not move those, so it is refused. */
    static void checkLinks(String otherName, Map<String, Long> links) {
        Map<String, Long> used = new TreeMap<>();
        links.forEach((table, n) -> { if (n != null && n > 0) used.put(table, n); });
        if (used.isEmpty()) return;
        throw new IllegalStateException("'" + otherName + "' has " + describeLinks(used) + " linked to it. Keep that record and merge the other one into it instead");
    }

    static String describeLinks(Map<String, Long> links) {
        List<String> parts = new ArrayList<>();
        new TreeMap<>(links).forEach((table, n) -> parts.add(table.replace('_', ' ') + " (" + n + ")"));
        return String.join(", ", parts);
    }

    // ── Values ──────────────────────────────────────────────────────────────
    private static String text(String v) { return v == null || v.isBlank() ? null : v.strip(); }

    private static String addressText(Map<String, String> a) {
        if (a == null) return null;
        List<String> parts = new ArrayList<>();
        for (String k : ADDRESS_ORDER) if (text(a.get(k)) != null) parts.add(a.get(k).strip());
        new TreeSet<>(a.keySet()).forEach(k -> { if (!ADDRESS_ORDER.contains(k) && text(a.get(k)) != null) parts.add(a.get(k).strip()); });
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** How a field reads, or null when it is empty. */
    static String read(Snap s, String field) {
        return switch (field) {
            case "name" -> text(s.name());
            case "email" -> text(s.email()) == null ? null : s.email().strip().toLowerCase(Locale.ROOT);
            case "phone" -> text(s.phone());
            case "taxNumber" -> text(s.taxNumber());
            case "address" -> addressText(s.address());
            case "owner" -> s.ownerId() == null ? null : s.ownerId().toString();
            case "source" -> s.source() == null ? null : s.source().name();
            case "dealValue" -> s.dealValue() == null ? null : s.dealValue().stripTrailingZeros().toPlainString();
            case "closeDate" -> s.closeDate() == null ? null : s.closeDate().toString();
            default -> field.startsWith(CUSTOM) ? text(s.custom() == null ? null : s.custom().get(field.substring(CUSTOM.length()))) : null;
        };
    }

    private static boolean same(String field, String a, String b) {
        if ("phone".equals(field)) return a.replaceAll("\\D", "").equals(b.replaceAll("\\D", ""));
        return a.equalsIgnoreCase(b);
    }

    /** Fields where both have a value and they differ, built-in fields first, then custom fields by key. */
    static List<Conflict> conflicts(Snap keep, Snap other, Map<String, String> customLabels) {
        List<Conflict> out = new ArrayList<>();
        List<String> fields = new ArrayList<>(FIELDS);
        Set<String> keys = new TreeSet<>();
        if (keep.custom() != null) keys.addAll(keep.custom().keySet());
        if (other.custom() != null) keys.addAll(other.custom().keySet());
        keys.forEach(k -> fields.add(CUSTOM + k));
        for (String f : fields) {
            String a = read(keep, f), b = read(other, f);
            if (a == null || b == null || same(f, a, b)) continue;
            out.add(new Conflict(f, f.startsWith(CUSTOM) ? customLabels.getOrDefault(f.substring(CUSTOM.length()), f.substring(CUSTOM.length())) : LABELS.get(f), a, b));
        }
        return out;
    }

    /** Which fields the person can choose to take from the other record. Unknown names are refused. */
    static Set<String> checkTake(List<String> take, Snap keep, Snap other) {
        Set<String> out = new LinkedHashSet<>();
        if (take == null) return out;
        for (String f : take) {
            if (f == null || (!FIELDS.contains(f) && !f.startsWith(CUSTOM))) throw new IllegalArgumentException("Unknown field '" + f + "'");
            if (read(other, f) == null) throw new IllegalArgumentException("The other record has nothing to take for " + (f.startsWith(CUSTOM) ? f.substring(CUSTOM.length()) : LABELS.get(f)));
            out.add(f);
        }
        return out;
    }

    /**
     * The values the kept record ends up with: its own, except a blank is filled from the other and any field in take is the other's.
     * Notes are never lost: when both have notes and they differ, the other's are added under a heading.
     */
    static Snap merge(Snap keep, Snap other, Set<String> take, String otherName) {
        return new Snap(
                pick("name", keep, other, take).name(), pick("email", keep, other, take).email(), pick("phone", keep, other, take).phone(),
                pick("taxNumber", keep, other, take).taxNumber(), mergeNotes(keep.notes(), other.notes(), otherName),
                pick("address", keep, other, take).address(), pick("owner", keep, other, take).ownerId(), pick("source", keep, other, take).source(),
                pick("dealValue", keep, other, take).dealValue(), pick("closeDate", keep, other, take).closeDate(), mergeCustom(keep, other, take));
    }

    /** The snap whose value for this field is used: the other's when chosen or when the kept one is blank. */
    private static Snap pick(String field, Snap keep, Snap other, Set<String> take) {
        boolean useOther = read(other, field) != null && (take.contains(field) || read(keep, field) == null);
        return useOther ? other : keep;
    }

    private static Map<String, String> mergeCustom(Snap keep, Snap other, Set<String> take) {
        Map<String, String> out = new LinkedHashMap<>(keep.custom() == null ? Map.of() : keep.custom());
        if (other.custom() != null) {
            new TreeMap<>(other.custom()).forEach((k, v) -> {
                String mine = text(out.get(k));
                if (text(v) != null && (mine == null || take.contains(CUSTOM + k))) out.put(k, v.strip());
            });
        }
        return out;
    }

    static String mergeNotes(String keep, String other, String otherName) {
        String a = text(keep), b = text(other);
        if (b == null) return keep;
        if (a == null) return b;
        if (a.equalsIgnoreCase(b)) return keep;
        return a + "\n\n— Notes from merged record " + otherName + " —\n" + b;
    }

    /** The field ids whose value changed on the kept record. */
    static List<String> filled(Snap before, Snap after) {
        List<String> out = new ArrayList<>();
        List<String> fields = new ArrayList<>(FIELDS);
        Set<String> keys = new TreeSet<>();
        if (after.custom() != null) keys.addAll(after.custom().keySet());
        keys.forEach(k -> fields.add(CUSTOM + k));
        for (String f : fields) {
            String a = read(before, f), b = read(after, f);
            if (b != null && !b.equals(a)) out.add(f);
        }
        if (!java.util.Objects.equals(text(before.notes()), text(after.notes()))) out.add("notes");
        return out;
    }

    /** Tags from both, the kept record's first. */
    static Set<String> mergeTags(Set<String> keep, Set<String> other) {
        Set<String> out = new LinkedHashSet<>(keep == null ? Set.of() : keep);
        if (other != null) out.addAll(other);
        return out;
    }

    // ── Company groups ──────────────────────────────────────────────────────
    /** The parent links once other has been folded into keep: other's branches move under keep, and keep leaves other's group. */
    static Map<UUID, UUID> linksAfter(Map<UUID, UUID> links, UUID keep, UUID other) {
        Map<UUID, UUID> out = new HashMap<>();
        links.forEach((child, parent) -> {
            if (child.equals(other)) return;
            UUID p = parent.equals(other) ? (child.equals(keep) ? links.get(other) : keep) : parent;
            if (p != null) out.put(child, p);
        });
        return out;
    }

    /** Throws when the merged groups would loop or be deeper than allowed. */
    static void checkGroups(Map<UUID, UUID> after) {
        for (UUID id : after.keySet()) {
            Set<UUID> seen = new LinkedHashSet<>();
            UUID cur = id;
            while (cur != null) {
                if (!seen.add(cur)) throw new IllegalArgumentException("Merging these would put a company group in a loop. Move one of them out of the group first");
                cur = after.get(cur);
            }
        }
        for (UUID id : after.values()) {
            if (!after.containsKey(id) && HierarchyRules.height(id, after) > HierarchyRules.MAX_LEVELS) {
                throw new IllegalArgumentException("Merging these would make a company group more than " + HierarchyRules.MAX_LEVELS + " levels deep");
            }
        }
    }
}
