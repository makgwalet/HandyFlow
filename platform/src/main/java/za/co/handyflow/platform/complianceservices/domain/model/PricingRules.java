package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * How the practice prices a service from its price book line. Pure, so every worked example can be a test.
 * Prices are ex VAT. Disbursements (travel, government fees) are passed on at cost: no complexity, no surcharge, no
 * discount, and VAT only where the person says the cost carries it. The calculator recommends; it never changes an
 * agreed fee.
 */
public final class PricingRules {

    private PricingRules() {}

    public static final Set<String> TAX_TREATMENTS = Set.of("STANDARD", "ZERO_RATED", "EXEMPT");
    public static final Set<String> UNIT_MODELS = Set.of("PER_DOCUMENT", "PER_REGISTRATION", "PER_SITE", "PER_EMPLOYEE");
    public static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(200);
    public static final BigDecimal MAX_MONEY = new BigDecimal("100000000");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** One priced line of the book, with the hourly rate already looked up for the grade asked for. */
    public record Line(String model, BigDecimal baseFee, BigDecimal unitRate, BigDecimal hourlyRate, BigDecimal minFee, BigDecimal urgentPct, String taxTreatment) {}
    public record Extra(String description, BigDecimal quantity, BigDecimal unitPrice) {}
    public record Disbursement(String description, BigDecimal amount, boolean vatable) {}
    public record Inputs(BigDecimal units, BigDecimal hours, BigDecimal complexityPct, boolean urgent, List<Extra> extras,
                         List<Disbursement> disbursements, BigDecimal discountPct) {}
    public record Part(String label, BigDecimal amount) {}
    public record Result(List<Part> parts, BigDecimal professionalBeforeDiscount, BigDecimal discount, BigDecimal professional,
                         BigDecimal disbursements, BigDecimal vat, BigDecimal vatRatePct, BigDecimal total, String discountApprover) {}

    public static BigDecimal money(BigDecimal raw, String what) {
        if (raw == null) return null;
        BigDecimal m = raw.setScale(2, RoundingMode.HALF_UP);
        if (m.signum() < 0 || m.compareTo(MAX_MONEY) > 0) throw new IllegalArgumentException(what + " must be between 0 and 100 000 000");
        return m;
    }

    public static BigDecimal percent(BigDecimal raw, String what, BigDecimal max) {
        if (raw == null) return null;
        BigDecimal p = raw.setScale(2, RoundingMode.HALF_UP);
        if (p.signum() < 0 || p.compareTo(max) > 0) throw new IllegalArgumentException(what + " must be between 0 and " + max.stripTrailingZeros().toPlainString() + " percent");
        return p;
    }

    public static String grade(String raw) {
        String g = raw == null ? "" : raw.trim().toUpperCase().replaceAll("[\\s-]+", "_");
        if (!g.matches("[A-Z0-9_]{2,30}")) throw new IllegalArgumentException("A grade uses letters, digits and underscores, 2 to 30 characters");
        return g;
    }

    public static String taxTreatment(String raw) {
        String t = raw == null || raw.isBlank() ? "STANDARD" : raw.trim().toUpperCase();
        if (!TAX_TREATMENTS.contains(t)) throw new IllegalArgumentException("taxTreatment must be one of " + TAX_TREATMENTS.stream().sorted().toList());
        return t;
    }

    /** What a line must have to be priced on its model. Empty means it can be used. */
    public static List<String> lineGaps(String model, BigDecimal baseFee, BigDecimal unitRate, String hourlyGrade) {
        List<String> g = new ArrayList<>();
        if (model == null) { g.add("pricing model"); return g; }
        switch (model) {
            case "FIXED", "PROJECT", "RETAINER" -> { if (baseFee == null) g.add("a base fee"); }
            case "HOURLY" -> { if (hourlyGrade == null) g.add("an hourly grade"); }
            case "HYBRID" -> { if (baseFee == null && unitRate == null && hourlyGrade == null) g.add("a base fee, a unit rate or an hourly grade"); }
            default -> { if (UNIT_MODELS.contains(model) && unitRate == null) g.add("a rate per unit"); }
        }
        return g;
    }

    private static BigDecimal r(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public static Result price(Line line, Inputs in, BigDecimal vatPct) {
        List<Part> parts = new ArrayList<>();
        BigDecimal units = in.units(), hours = in.hours();
        BigDecimal amount = BigDecimal.ZERO;
        String m = line.model();
        boolean unitModel = UNIT_MODELS.contains(m);
        if (m.equals("FIXED") || m.equals("PROJECT") || m.equals("RETAINER")) {
            if (line.baseFee() == null) throw new IllegalArgumentException("This service has no base fee in the price book");
            amount = line.baseFee();
            parts.add(new Part(m.equals("RETAINER") ? "Monthly retainer" : "Fee", r(amount)));
        }
        if (m.equals("HOURLY") || m.equals("HYBRID")) {
            if (hours != null && hours.signum() > 0) {
                if (line.hourlyRate() == null) throw new IllegalArgumentException("Choose a grade that has an hourly rate in the price book");
                BigDecimal h = r(hours.multiply(line.hourlyRate()));
                parts.add(new Part(hours.stripTrailingZeros().toPlainString() + " hours", h));
                amount = amount.add(h);
            } else if (m.equals("HOURLY")) {
                throw new IllegalArgumentException("Say how many hours");
            }
        }
        if (m.equals("HYBRID") && line.baseFee() != null) { amount = amount.add(line.baseFee()); parts.add(new Part("Base fee", r(line.baseFee()))); }
        if (unitModel || (m.equals("HYBRID") && units != null && units.signum() > 0)) {
            if (units == null || units.signum() <= 0) throw new IllegalArgumentException("Say how many units");
            if (line.unitRate() == null) throw new IllegalArgumentException("This service has no rate per unit in the price book");
            if (line.baseFee() != null && unitModel) { amount = amount.add(line.baseFee()); parts.add(new Part("Base fee", r(line.baseFee()))); }
            BigDecimal u = r(units.multiply(line.unitRate()));
            parts.add(new Part(units.stripTrailingZeros().toPlainString() + " x " + r(line.unitRate()).toPlainString(), u));
            amount = amount.add(u);
        }
        amount = r(amount);
        if (line.minFee() != null && amount.compareTo(line.minFee()) < 0) {
            parts.add(new Part("Raised to the minimum fee", r(line.minFee().subtract(amount))));
            amount = r(line.minFee());
        }
        BigDecimal complexity = in.complexityPct() == null ? BigDecimal.ZERO : percent(in.complexityPct(), "Complexity", MAX_PERCENT);
        if (complexity.signum() > 0) {
            BigDecimal add = r(amount.multiply(complexity).divide(HUNDRED, 4, RoundingMode.HALF_UP));
            parts.add(new Part("Complexity " + complexity.stripTrailingZeros().toPlainString() + "%", add));
            amount = amount.add(add);
        }
        if (in.urgent()) {
            if (line.urgentPct() == null) throw new IllegalArgumentException("This service has no urgent surcharge in the price book");
            BigDecimal add = r(amount.multiply(line.urgentPct()).divide(HUNDRED, 4, RoundingMode.HALF_UP));
            parts.add(new Part("Urgent " + line.urgentPct().stripTrailingZeros().toPlainString() + "%", add));
            amount = amount.add(add);
        }
        if (in.extras() != null) {
            for (Extra e : in.extras()) {
                if (e.quantity() == null || e.quantity().signum() <= 0) throw new IllegalArgumentException("An extra needs a quantity above 0");
                BigDecimal p = money(e.unitPrice(), "An extra's price");
                if (p == null) throw new IllegalArgumentException("An extra needs a price");
                BigDecimal x = r(e.quantity().multiply(p));
                parts.add(new Part(e.quantity().stripTrailingZeros().toPlainString() + " x " + (e.description() == null ? "extra" : e.description().trim()), x));
                amount = amount.add(x);
            }
        }
        amount = r(amount);
        BigDecimal discountPct = in.discountPct() == null ? BigDecimal.ZERO : percent(in.discountPct(), "Discount", HUNDRED);
        BigDecimal discount = r(amount.multiply(discountPct).divide(HUNDRED, 4, RoundingMode.HALF_UP));
        if (discount.signum() > 0) parts.add(new Part("Discount " + discountPct.stripTrailingZeros().toPlainString() + "%", discount.negate()));
        BigDecimal professional = amount.subtract(discount);

        BigDecimal disb = BigDecimal.ZERO, vatableDisb = BigDecimal.ZERO;
        if (in.disbursements() != null) {
            for (Disbursement d : in.disbursements()) {
                BigDecimal a = money(d.amount(), "A disbursement");
                if (a == null) throw new IllegalArgumentException("A disbursement needs an amount");
                disb = disb.add(a);
                if (d.vatable()) vatableDisb = vatableDisb.add(a);
                parts.add(new Part("Disbursement: " + (d.description() == null ? "cost" : d.description().trim()), a));
            }
        }
        BigDecimal rate = vatPct == null ? BigDecimal.ZERO : vatPct;
        BigDecimal vatBase = vatableDisb.add("STANDARD".equals(line.taxTreatment()) ? professional : BigDecimal.ZERO);
        BigDecimal vat = r(vatBase.multiply(rate).divide(HUNDRED, 4, RoundingMode.HALF_UP));
        BigDecimal total = professional.add(disb).add(vat);
        return new Result(parts, amount, discount, professional, r(disb), vat, rate, r(total), discountApprover(discountPct));
    }

    /** Who may agree a discount of this size. These are proposed starting thresholds, not approved policy. */
    public static String discountApprover(BigDecimal pct) {
        if (pct == null || pct.compareTo(new BigDecimal("5")) <= 0) return "CONSULTANT";
        if (pct.compareTo(new BigDecimal("10")) <= 0) return "COMPLIANCE_MANAGER";
        if (pct.compareTo(new BigDecimal("15")) <= 0) return "PRACTICE_OWNER";
        return "PRACTICE_OWNER_WITH_EXCEPTION";
    }

    /** What a job costs the practice: labour, external costs and an overhead share of the labour. */
    public static BigDecimal cost(BigDecimal hours, BigDecimal costRate, BigDecimal external, BigDecimal overheadPct) {
        BigDecimal labour = hours == null || costRate == null ? BigDecimal.ZERO : hours.multiply(costRate);
        BigDecimal oh = overheadPct == null ? BigDecimal.ZERO : labour.multiply(percent(overheadPct, "Overhead", HUNDRED)).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal ext = external == null ? BigDecimal.ZERO : money(external, "External cost");
        return r(labour.add(oh).add(ext));
    }

    /** Price that gives a target gross margin. Margin is not markup: price = cost / (1 - margin). */
    public static BigDecimal priceForMargin(BigDecimal cost, BigDecimal marginPct) {
        if (cost == null || cost.signum() < 0) throw new IllegalArgumentException("Cost must be 0 or more");
        if (marginPct == null || marginPct.signum() < 0 || marginPct.compareTo(new BigDecimal("95")) > 0) throw new IllegalArgumentException("Margin must be between 0 and 95 percent");
        BigDecimal keep = BigDecimal.ONE.subtract(marginPct.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        return cost.divide(keep, 2, RoundingMode.HALF_UP);
    }

    /** Rounds a price up to the next step, for example 2666.67 to 2700 with a step of 50. */
    public static BigDecimal roundUpTo(BigDecimal price, BigDecimal step) {
        if (step == null || step.signum() <= 0) return price.setScale(2, RoundingMode.HALF_UP);
        return price.divide(step, 0, RoundingMode.CEILING).multiply(step).setScale(2, RoundingMode.HALF_UP);
    }

    /** Gross margin as a percentage of the price. Null when there is no price. */
    public static BigDecimal grossMargin(BigDecimal price, BigDecimal cost) {
        if (price == null || price.signum() <= 0) return null;
        return price.subtract(cost).multiply(HUNDRED).divide(price, 2, RoundingMode.HALF_UP);
    }
}
