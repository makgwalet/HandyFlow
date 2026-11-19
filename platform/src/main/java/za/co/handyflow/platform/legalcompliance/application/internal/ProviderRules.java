package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.AccessScope;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderClientStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.ReviewCycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure rules for provider–client access: invitation codes, scopes, review dates and the single access decision. */
public final class ProviderRules {

    private ProviderRules() {}

    /** No 0/O/1/I/L so a code read out over the phone is not misheard. */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int CODE_LENGTH = 12;
    public static final int INVITE_DAYS = 14;

    public static String newCode(SecureRandom rnd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Upper-cases and strips everything that is not a letter or digit, so "abcd efgh-jkmn" matches "ABCD-EFGH-JKMN". */
    public static String normalise(String code) {
        return code == null ? "" : code.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    public static boolean looksLikeCode(String code) { return normalise(code).length() == CODE_LENGTH; }

    /** Only the hash is stored; a database read therefore never reveals a usable code. */
    public static String hash(String code) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(normalise(code).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean inviteExpired(Instant expiresAt, Instant now) { return expiresAt == null || !now.isBefore(expiresAt); }

    public static String scopesToCsv(Set<AccessScope> s) { return s.stream().sorted().map(Enum::name).collect(Collectors.joining(",")); }

    public static Set<AccessScope> parseScopes(String csv) {
        Set<AccessScope> out = EnumSet.noneOf(AccessScope.class);
        if (csv == null || csv.isBlank()) return out;
        for (String p : csv.split(",")) out.add(AccessScope.valueOf(p.trim()));
        return out;
    }

    public static LocalDate nextReview(LocalDate from, ReviewCycle cycle) {
        return switch (cycle) { case MONTHLY -> from.plusMonths(1); case QUARTERLY -> from.plusMonths(3); case ANNUALLY -> from.plusYears(1); };
    }

    public static boolean reviewOverdue(ProviderClientStatus status, LocalDate nextDue, LocalDate today) {
        return status == ProviderClientStatus.ACTIVE && nextDue != null && today.isAfter(nextDue);
    }

    /** Reminder on the due date and then weekly while it stays overdue. */
    public static boolean reminderDue(LocalDate nextDue, LocalDate today) {
        if (nextDue == null || today.isBefore(nextDue)) return false;
        return java.time.temporal.ChronoUnit.DAYS.between(nextDue, today) % 7 == 0;
    }

    /** Only the party that suspended may lift a suspension, so a client's suspension cannot be undone by the consultancy. */
    public static boolean canResume(String suspendedBy, String side) { return suspendedBy != null && suspendedBy.equals(side); }

    /**
     * The one decision behind every cross-tenant read. Reasons are deliberately generic about the client's side: a consultant is told "no access", never why a client acted.
     * manager: has PROVIDER_MANAGE (or ADMIN). Otherwise the user must hold an active assignment to this client.
     */
    public static String denial(ProviderClientStatus status, Set<AccessScope> granted, AccessScope needed, boolean manager, boolean assigned) {
        if (status != ProviderClientStatus.ACTIVE) return "Access to this client is not currently active";
        if (!granted.contains(needed)) return "This client has not granted access to " + needed.name().toLowerCase();
        if (!manager && !assigned) return "You are not assigned to this client";
        return null;
    }
}
