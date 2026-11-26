package za.co.handyflow.platform.payrollbureau.domain.rules;

import za.co.handyflow.platform.shared.StatutoryYearRules;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A short fingerprint (SHA-256) of the exact statutory figures a payslip was calculated with. If the approved rules of a
 * year are ever changed, payslips made before the change keep the old fingerprint, so the difference can be seen.
 * Same figures always give the same fingerprint, whatever the decimal scale they were stored with.
 */
public final class RulesFingerprint {

    /** Bumped whenever the way the bureau applies the rules changes, so payslips can be told apart by method as well as by figures. */
    public static final String FORMULA_VERSION = "shared-paye-1";

    /** The same rules applied to weekly and fortnightly pay: annualised by 52 or 26, UIF capped per calendar month across runs. */
    public static final String FORMULA_VERSION_PERIODIC = "shared-paye-periodic-1";

    public static String formulaFor(int periodsPerYear) {
        return periodsPerYear == 12 ? FORMULA_VERSION : FORMULA_VERSION_PERIODIC;
    }

    private RulesFingerprint() {}

    public static String of(StatutoryYearRules r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.taxYear()).append('|').append(r.effectiveFrom()).append('|').append(r.effectiveTo());
        for (StatutoryYearRules.Bracket b : r.brackets()) {
            sb.append("|B:").append(n(b.from())).append(',').append(n(b.to())).append(',').append(n(b.baseTax())).append(',').append(n(b.ratePercent()));
        }
        for (BigDecimal v : new BigDecimal[]{r.primaryRebate(), r.secondaryRebate(), r.tertiaryRebate(), r.mtcTaxpayer(),
                r.mtcFirstDependant(), r.mtcAdditional(), r.uifMonthlyCeiling(), r.uifRateEmployee(), r.uifRateEmployer(),
                r.sdlRate(), r.sdlThreshold(), r.retirementCapAmount(), r.retirementCapPct(), r.travelTaxablePct()}) {
            sb.append('|').append(n(v));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String n(BigDecimal v) {
        if (v == null) return "-";
        return v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
