package za.co.handyflow.platform.security.application.internal;

/**
 * Ammunition accounting on firearm issue and return. Pure.
 * Each check returns null when fine, or the error code to refuse with.
 */
final class AmmoRules {

    static final int MAX_ROUNDS = 2000;

    private AmmoRules() {}

    /** Rounds handed out with the firearm. Optional: null means "not recorded". */
    static String checkIssue(Integer rounds) {
        if (rounds == null) return null;
        return rounds < 0 || rounds > MAX_ROUNDS ? "AMMO_COUNT_INVALID" : null;
    }

    /**
     * @param issued   rounds recorded at issue, null if none were recorded
     * @param returned rounds handed back
     * @param reason   explanation when fewer came back than went out
     */
    static String checkReturn(Integer issued, Integer returned, String reason) {
        if (issued == null) {
            // Nothing recorded at issue: accept a sane count, but there is nothing to compare it with.
            return returned != null && (returned < 0 || returned > MAX_ROUNDS) ? "AMMO_COUNT_INVALID" : null;
        }
        if (returned == null) return "AMMO_COUNT_REQUIRED";
        if (returned < 0 || returned > MAX_ROUNDS) return "AMMO_COUNT_INVALID";
        if (returned > issued) return "AMMO_RETURN_EXCEEDS_ISSUED";
        if (returned < issued && (reason == null || reason.isBlank())) return "AMMO_EXPLANATION_REQUIRED";
        return null;
    }

    /** Rounds not handed back. 0 when either count is missing. */
    static int shortfall(Integer issued, Integer returned) {
        return issued == null || returned == null ? 0 : Math.max(0, issued - returned);
    }

    /** "30 x 9mm", "30 rounds", or an em dash. */
    static String label(String calibre, Integer rounds) {
        if (rounds == null) return "—";
        String c = calibre == null ? "" : calibre.trim();
        return c.isEmpty() ? rounds + " rounds" : rounds + " x " + c;
    }
}
