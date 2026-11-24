package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Rules for the people behind a client and the sites it runs. Pure, so it is easy to test. */
public final class ClientPartyRules {

    private ClientPartyRules() {}

    public static final Set<String> ROLES = Set.of("DIRECTOR", "SHAREHOLDER", "BENEFICIAL_OWNER", "MEMBER", "TRUSTEE", "PARTNER", "PUBLIC_OFFICER");
    /** Roles that can carry an ownership percentage. */
    public static final Set<String> OWNERSHIP_ROLES = Set.of("SHAREHOLDER", "BENEFICIAL_OWNER", "MEMBER", "PARTNER");
    public static final Set<String> PARTY_TYPES = Set.of("PERSON", "ENTITY");
    public static final Set<String> ID_TYPES = Set.of("SA_ID", "PASSPORT", "REG_NO", "OTHER");
    public static final Set<String> SITE_TYPES = Set.of("HEAD_OFFICE", "BRANCH", "FACTORY", "WAREHOUSE", "SITE", "OTHER");
    /** The share at which a holder counts as a beneficial owner for the register. */
    public static final BigDecimal BENEFICIAL_OWNER_THRESHOLD = new BigDecimal("5");

    /** A South African ID number: 13 digits, a real birth date, and a correct check digit. */
    public static boolean validSaId(String id) {
        if (id == null || !id.matches("\\d{13}")) return false;
        int mm = Integer.parseInt(id.substring(2, 4)), dd = Integer.parseInt(id.substring(4, 6)), yy = Integer.parseInt(id.substring(0, 2));
        boolean date = false;
        for (int century : new int[]{1900, 2000}) {
            try { LocalDate.of(century + yy, mm, dd); date = true; } catch (java.time.DateTimeException ignored) { /* try the other century */ }
        }
        if (!date) return false;
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int d = id.charAt(12 - i) - '0';
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
        }
        return sum % 10 == 0;
    }

    public static String idNumber(String idType, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().replaceAll("[\\s-]", "").toUpperCase();
        if ("SA_ID".equals(idType) && !validSaId(v)) throw new IllegalArgumentException("That South African ID number is not valid");
        if (v.length() > 40) throw new IllegalArgumentException("idNumber is too long");
        return v;
    }

    public static BigDecimal percent(String role, BigDecimal p) {
        if (p == null) return null;
        if (!OWNERSHIP_ROLES.contains(role)) throw new IllegalArgumentException("A " + role + " does not hold a percentage");
        if (p.signum() < 0 || p.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("ownershipPercent must be between 0 and 100");
        return p;
    }

    public static void dates(LocalDate appointed, LocalDate resigned) {
        if (appointed != null && resigned != null && resigned.isBefore(appointed)) throw new IllegalArgumentException("resignedOn cannot be before appointedOn");
    }

    /** Summary of the people on file, used to flag gaps. */
    public record Holding(String role, BigDecimal percent, boolean active) {}

    public record OwnershipSummary(BigDecimal shareholdingDeclared, boolean fullyDeclared, boolean hasDirector,
                                   boolean hasBeneficialOwner, boolean beneficialOwnerGap, int directors) {}

    public static OwnershipSummary summarise(List<Holding> holdings) {
        BigDecimal shares = BigDecimal.ZERO;
        boolean director = false, bo = false;
        boolean over5 = false;
        int directors = 0;
        for (Holding h : holdings) {
            if (!h.active()) continue;
            if ("DIRECTOR".equals(h.role())) { director = true; directors++; }
            if ("BENEFICIAL_OWNER".equals(h.role())) bo = true;
            if ("SHAREHOLDER".equals(h.role()) && h.percent() != null) {
                shares = shares.add(h.percent());
                if (h.percent().compareTo(BENEFICIAL_OWNER_THRESHOLD) >= 0) over5 = true;
            }
        }
        // holders of 5% or more are beneficial owners, so someone must be recorded as one
        boolean gap = over5 && !bo;
        return new OwnershipSummary(shares, shares.compareTo(new BigDecimal("100")) == 0, director, bo, gap, directors);
    }

    /** True if adding or changing a shareholding would take the active total above 100%. */
    public static boolean exceedsWhole(BigDecimal otherActiveShares, BigDecimal mine) {
        return mine != null && otherActiveShares.add(mine).compareTo(new BigDecimal("100")) > 0;
    }
}
