package za.co.handyflow.platform.legalcompliance.application.internal;

/**
 * Pure classification of how well an obligation is covered. "Active" means a control is in operation; only a control whose operating
 * effectiveness has been established by testing counts as EFFECTIVE. Until the testing chunk exists no control can be, so every covered
 * obligation reads COVERED_UNTESTED — deliberately.
 */
public final class CoverageRules {
    private CoverageRules() {}

    public static String obligationState(int activeControls, int effectiveControls) {
        if (activeControls <= 0) return "UNCOVERED";
        return effectiveControls > 0 ? "COVERED_EFFECTIVE" : "COVERED_UNTESTED";
    }
}
