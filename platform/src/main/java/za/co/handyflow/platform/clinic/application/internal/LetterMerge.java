package za.co.handyflow.platform.clinic.application.internal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Merge fields in letter templates, such as {{patient.name}}. Pure. */
final class LetterMerge {
    private LetterMerge() {}

    static final List<String> FIELDS = List.of("patient.name", "patient.firstName", "patient.lastName", "patient.dob", "patient.age",
            "patient.idNumber", "patient.phone", "patient.address", "visit.date", "visit.reason", "visit.diagnosis", "visit.treatment",
            "doctor.name", "doctor.hpcsa", "doctor.practiceNumber", "practice.name", "today", "recipient.name", "recipient.company");

    /** Everything a letter can be filled from. A visit-less letter leaves the visit and doctor parts null. */
    record Source(String firstName, String lastName, LocalDate dob, String idNumber, String phone, String address,
                  LocalDate visitDate, String reason, String diagnosis, String treatment,
                  String doctorName, String hpcsa, String practiceNumber, String practiceName, LocalDate today,
                  String recipientName, String recipientCompany) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** "7 months" under two years, otherwise "7 years". */
    static String age(LocalDate dob, LocalDate today) {
        if (dob == null || today == null || dob.isAfter(today)) return null;
        long months = ChronoUnit.MONTHS.between(dob, today);
        if (months < 24) return months + (months == 1 ? " month" : " months");
        long years = months / 12;
        return years + (years == 1 ? " year" : " years");
    }

    static Map<String, String> values(Source s) {
        Map<String, String> v = new HashMap<>();
        String first = clean(s.firstName()), last = clean(s.lastName());
        v.put("patient.name", ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim());
        v.put("patient.firstName", first);
        v.put("patient.lastName", last);
        v.put("patient.dob", s.dob() == null ? null : s.dob().format(DATE));
        v.put("patient.age", age(s.dob(), s.today()));
        v.put("patient.idNumber", clean(s.idNumber()));
        v.put("patient.phone", clean(s.phone()));
        v.put("patient.address", clean(s.address()));
        v.put("visit.date", s.visitDate() == null ? null : s.visitDate().format(DATE));
        v.put("visit.reason", clean(s.reason()));
        v.put("visit.diagnosis", clean(s.diagnosis()));
        v.put("visit.treatment", clean(s.treatment()));
        v.put("doctor.name", clean(s.doctorName()));
        v.put("doctor.hpcsa", clean(s.hpcsa()));
        v.put("doctor.practiceNumber", clean(s.practiceNumber()));
        v.put("practice.name", clean(s.practiceName()));
        v.put("today", s.today() == null ? null : s.today().format(DATE));
        v.put("recipient.name", clean(s.recipientName()));
        v.put("recipient.company", clean(s.recipientCompany()));
        return v;
    }

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z.]+)\\s*}}");

    /** The merge fields used in the text that this system does not know, in order of first use. */
    static List<String> unknown(String text) {
        Set<String> bad = new LinkedHashSet<>();
        if (text == null) return List.of();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) if (!FIELDS.contains(m.group(1))) bad.add(m.group(1));
        return List.copyOf(bad);
    }

    /** Fills each known field; a field with no value becomes a dash so the gap is visible, never an empty hole. */
    static String render(String text, Map<String, String> values) {
        if (text == null) return null;
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(v == null || v.isBlank() ? "—" : v));
        }
        m.appendTail(out);
        return out.toString();
    }
}
