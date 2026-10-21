package za.co.handyflow.platform.crm.application.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.ReportFrequency;
import za.co.handyflow.platform.crm.domain.model.ReportType;

/** Rules for scheduled reports: what a schedule must have, and which day it goes out. Pure, no database. */
final class ReportRules {
    static final int MAX_SCHEDULES = 20, MAX_RECIPIENTS = 20;

    private ReportRules() {}

    static void check(String name, ReportType type, ReportFrequency frequency, List<UUID> recipients) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the report");
        if (name.strip().length() > 120) throw new IllegalArgumentException("The name can be at most 120 characters");
        if (type == null) throw new IllegalArgumentException("Choose which report to send");
        if (frequency == null) throw new IllegalArgumentException("Choose how often to send it");
        if (recipients != null && recipients.size() > MAX_RECIPIENTS) throw new IllegalArgumentException("A report can go to at most " + MAX_RECIPIENTS + " people");
        if (recipients != null && recipients.stream().distinct().count() != recipients.size()) throw new IllegalArgumentException("Each person can only be listed once");
    }

    /** Due on its day (Monday, or the 1st), and not already sent today. A schedule is never due twice in a day. */
    static boolean isDue(ReportFrequency frequency, LocalDate lastSent, LocalDate today) {
        boolean itsDay = frequency == ReportFrequency.WEEKLY ? today.getDayOfWeek() == DayOfWeek.MONDAY : today.getDayOfMonth() == 1;
        return itsDay && (lastSent == null || lastSent.isBefore(today));
    }

    static String describe(ReportType type, ReportFrequency frequency) {
        String what = switch (type) { case PIPELINE -> "Pipeline summary"; case SOURCES -> "Lead sources"; case FUNNEL -> "Sales funnel"; };
        return what + ", " + (frequency == ReportFrequency.WEEKLY ? "every Monday" : "on the 1st of each month");
    }
}
