package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rules for billing events, with no database in them. A billing event is one scheduled bill from an active agreement.
 * A person raises it as a draft invoice; it is never issued from here, so nobody is billed until someone reviews it in Invoicing.
 */
public final class BillingRules {

    private BillingRules() {}

    public static final String SCHEDULED = "SCHEDULED";
    public static final String INVOICED = "INVOICED";
    public static final String SKIPPED = "SKIPPED";
    public static final Set<String> STATUSES = Set.of(SCHEDULED, INVOICED, SKIPPED);

    /** A bill can be raised this many days before its date. A proposed starting value, not approved policy. */
    public static final int LEAD_DAYS = 7;
    /** Events are made this far ahead of today, so a long or open-ended agreement does not fill the list at once. */
    public static final int HORIZON_DAYS = 60;
    public static final int MIN_NOTE = 10;

    /** Schedule dates that fall inside the horizon and have no event yet. Past dates are included so nothing is missed. */
    public static List<LocalDate> datesToGenerate(List<LocalDate> schedule, Set<LocalDate> existing, LocalDate today) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate limit = today.plusDays(HORIZON_DAYS);
        for (LocalDate d : schedule) {
            if (d.isAfter(limit) || existing.contains(d)) continue;
            out.add(d);
        }
        return out;
    }

    public static String describe(String agreementNumber, String title, LocalDate dueDate) {
        String t = title == null || title.isBlank() ? "" : " " + title.trim();
        String text = "Compliance services, agreement " + agreementNumber + t + ", billing of " + dueDate;
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    /** Why this event cannot be raised now, or null when it can. */
    public static String raiseProblem(String eventStatus, LocalDate dueDate, String agreementStatus, LocalDate agreementEnd, boolean hasCustomer, LocalDate today) {
        if (!SCHEDULED.equals(eventStatus)) return "Only a scheduled bill can be raised";
        if (!AgreementRules.ACTIVE.equals(agreementStatus)) return "The agreement is " + agreementStatus.toLowerCase() + ", so nothing can be billed on it";
        if (agreementEnd != null && dueDate.isAfter(agreementEnd)) return "This bill falls after the agreement's end date";
        if (!hasCustomer) return "Link this client to a customer in Invoicing before raising an invoice";
        if (dueDate.isAfter(today.plusDays(LEAD_DAYS))) return "This bill is not due yet. It can be raised from " + dueDate.minusDays(LEAD_DAYS);
        return null;
    }

    public static long daysLate(LocalDate dueDate, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
    }

    /** What a person should do with a scheduled bill today. */
    public static String stage(String status, LocalDate dueDate, LocalDate today) {
        if (!SCHEDULED.equals(status)) return status;
        if (dueDate.isBefore(today)) return "OVERDUE_TO_RAISE";
        if (!dueDate.isAfter(today.plusDays(LEAD_DAYS))) return "DUE";
        return "UPCOMING";
    }

    /** A released invoice must be cancelled or gone in Invoicing, so the same bill cannot be raised on top of a live invoice. */
    public static String releaseProblem(String eventStatus, String invoiceStatus) {
        if (!INVOICED.equals(eventStatus)) return "Only a bill with an invoice can be released";
        if (invoiceStatus != null && !"CANCELLED".equals(invoiceStatus)) {
            return "The invoice is " + invoiceStatus.toLowerCase().replace('_', ' ') + " in Invoicing. Cancel it there first, or raise a credit note";
        }
        return null;
    }

    public static List<String> actionsFor(String status) {
        return switch (status == null ? "" : status) {
            case SCHEDULED -> List.of("raise", "skip");
            case INVOICED -> List.of("release");
            case SKIPPED -> List.of("restore");
            default -> List.of();
        };
    }
}
