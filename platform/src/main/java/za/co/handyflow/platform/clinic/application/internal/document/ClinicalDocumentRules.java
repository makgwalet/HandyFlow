package za.co.handyflow.platform.clinic.application.internal.document;

import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The sick note and the referral letter as {@link DocumentModel}s (patch 0200). Pure: no Spring, no database, no renderer. What goes on
 * the page is decided here; the letterhead, the number and the verification line are added when the document is issued.
 */
public final class ClinicalDocumentRules {

    private ClinicalDocumentRules() {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public record Person(String fullName, String idNumber, LocalDate dateOfBirth, String gender) {}
    public record Signer(String name, String line) {}

    /** What was filled in, kept with the issued document so it can be shown again exactly as it was. */
    public record Built(DocumentModel model, Map<String, Object> snapshot) {}

    public static String fmt(LocalDate d) { return d == null ? "—" : d.format(DATE); }

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** The practitioner's name with one "Dr." in front, whether or not the stored name already has it. */
    public static String drName(String fullName) {
        if (fullName == null) return "";
        String s = fullName.trim();
        return s.matches("(?i)dr\\b.*") ? s : "Dr. " + s;
    }

    /** Dates of absence: either may be blank, but the end may not be before the start. */
    public static void checkAbsence(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) throw new IllegalArgumentException("The sick note cannot end before it starts");
    }

    public static Built sickNote(Letterhead head, Person patient, Signer signer, LocalDate examinedOn, LocalDate unfitFrom, LocalDate unfitTo,
                                 String diagnosis, String notes, LocalDate issuedOn) {
        checkAbsence(unfitFrom, unfitTo);
        if (patient == null) throw new IllegalArgumentException("A sick note needs a patient");
        List<String[]> person = new ArrayList<>();
        person.add(new String[]{"Full name", patient.fullName()});
        if (clean(patient.idNumber()) != null) person.add(new String[]{"ID number", patient.idNumber().trim()});
        if (patient.dateOfBirth() != null) person.add(new String[]{"Date of birth", fmt(patient.dateOfBirth())});
        if (clean(patient.gender()) != null) person.add(new String[]{"Gender", patient.gender().trim()});

        List<Block> b = new ArrayList<>();
        b.add(new Heading(1, "Medical certificate"));
        b.add(new TextBlock("Date issued: " + fmt(issuedOn), false, true));
        b.add(new Heading(2, "Patient"));
        b.add(new KeyValues(person));
        b.add(new Heading(2, "Certificate of illness"));
        b.add(TextBlock.of("This is to certify that the above-named patient was examined on " + fmt(examinedOn) + " and was found to be unfit for duty."));
        b.add(new KeyValues(List.<String[]>of(new String[]{"Unfit from", fmt(unfitFrom)}, new String[]{"Unfit until", fmt(unfitTo)})));
        if (clean(diagnosis) != null) {
            b.add(new Heading(3, "Diagnosis / clinical impression"));
            b.add(TextBlock.of(diagnosis.trim()));
        }
        if (clean(notes) != null) {
            b.add(new Heading(3, "Notes"));
            b.add(TextBlock.of(notes.trim()));
        }
        b.add(new Spacer(10));
        b.add(signature("Yours faithfully,", signer));

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("patientName", patient.fullName());
        s.put("patientIdNumber", clean(patient.idNumber()));
        s.put("patientDateOfBirth", patient.dateOfBirth() == null ? null : patient.dateOfBirth().toString());
        s.put("examinedOn", examinedOn == null ? null : examinedOn.toString());
        s.put("unfitFrom", unfitFrom == null ? null : unfitFrom.toString());
        s.put("unfitUntil", unfitTo == null ? null : unfitTo.toString());
        s.put("diagnosis", clean(diagnosis));
        s.put("notes", clean(notes));
        s.put("signerName", signer == null ? null : signer.name());
        s.put("signerLine", signer == null ? null : signer.line());
        return new Built(new DocumentModel("Medical certificate", head, b, null, true), s);
    }

    public static final List<String> URGENCIES = List.of("ROUTINE", "SEMI_URGENT", "URGENT");

    /** Blank means "not stated". Anything else must be one of the known levels, so a mistyped urgency is not printed as if it were meaningful. */
    public static String urgency(String raw) {
        String u = clean(raw);
        if (u == null) return null;
        String k = u.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!URGENCIES.contains(k)) throw new IllegalArgumentException("Urgency must be routine, semi-urgent or urgent");
        return k;
    }

    public static Built referral(Letterhead head, Person patient, Signer signer, String specialistName, String specialty, String reason, String urgency,
                                 String chiefComplaint, String diagnosis, List<String> icd10, String treatmentToDate, String additionalNotes, LocalDate issuedOn) {
        if (patient == null) throw new IllegalArgumentException("A referral needs a patient");
        String urg = urgency(urgency);
        String to = (clean(specialistName) != null ? specialistName.trim() : "Dear Colleague") + (clean(specialty) != null ? " (" + specialty.trim() + ")" : "");
        String dob = patient.dateOfBirth() == null ? "—" : fmt(patient.dateOfBirth());

        List<Block> b = new ArrayList<>();
        b.add(new Heading(1, "Referral letter"));
        b.add(new TextBlock(fmt(issuedOn), false, false));
        b.add(new TextBlock(to, true, false));
        b.add(new TextBlock("Re: " + patient.fullName() + " (DOB " + dob + ")", true, false));
        if (urg != null) b.add(new TextBlock("Urgency: " + urg.replace('_', ' ').toLowerCase(Locale.ROOT), true, false));
        b.add(TextBlock.of("I am referring the above patient for your assessment and management."));
        section(b, "Reason for referral", reason);
        section(b, "Chief complaint", chiefComplaint);
        section(b, "Diagnosis", diagnosis);
        if (icd10 != null && !icd10.isEmpty()) section(b, "ICD-10 codes", String.join(", ", icd10));
        section(b, "Treatment to date", treatmentToDate);
        section(b, "Additional notes", additionalNotes);
        b.add(new TextBlock("Please do not hesitate to contact the practice for any further clinical information.", false, true));
        b.add(signature("Kind regards,", signer));

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("patientName", patient.fullName());
        s.put("patientDateOfBirth", patient.dateOfBirth() == null ? null : patient.dateOfBirth().toString());
        s.put("specialistName", clean(specialistName));
        s.put("specialty", clean(specialty));
        s.put("urgency", urg);
        s.put("reason", clean(reason));
        s.put("chiefComplaint", clean(chiefComplaint));
        s.put("diagnosis", clean(diagnosis));
        s.put("icd10", icd10 == null ? List.of() : icd10);
        s.put("treatmentToDate", clean(treatmentToDate));
        s.put("additionalNotes", clean(additionalNotes));
        s.put("signerName", signer == null ? null : signer.name());
        s.put("signerLine", signer == null ? null : signer.line());
        return new Built(new DocumentModel("Referral letter", head, b, null, true), s);
    }

    // ── Patch 0203: prescription, visit summary, consent record ─────────────────────────────────────────────

    /** The prescriber as printed on a prescription. */
    public record Prescriber(String name, String hpcsa, String practiceNumber, String specialty) {}

    /** One medicine line, as recorded on the visit. */
    public record RxLine(String medicine, Integer schedule, String nappi, String dosage, String frequency, String duration,
                         String instructions, Integer quantity, int repeats) {}

    /** The statement the prescription PDF has always carried (kept word for word from the earlier PDF). */
    public static final String PRESCRIPTION_VALIDITY = "This prescription is valid for 30 days from the date of issue. "
            + "Issued under the Medicines and Related Substances Act (Act 101 of 1965).";

    public static final String NOT_SIGNED = "NOT ISSUED: this visit is not signed yet. Sign the visit to issue a numbered copy.";

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static List<String[]> personRows(Person p, boolean withGender) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Full name", p.fullName()});
        if (clean(p.idNumber()) != null) rows.add(new String[]{"ID number", p.idNumber().trim()});
        if (p.dateOfBirth() != null) rows.add(new String[]{"Date of birth", fmt(p.dateOfBirth())});
        if (withGender && clean(p.gender()) != null) rows.add(new String[]{"Gender", p.gender().trim()});
        return rows;
    }

    private static Map<String, Object> personSnapshot(Person p) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("patientName", p.fullName());
        s.put("patientIdNumber", clean(p.idNumber()));
        s.put("patientDateOfBirth", p.dateOfBirth() == null ? null : p.dateOfBirth().toString());
        return s;
    }

    /** "500 mg  three times a day  for 5 days. Take with food." */
    public static String directions(RxLine rx) {
        StringBuilder sb = new StringBuilder();
        if (clean(rx.dosage()) != null) sb.append(rx.dosage().trim()).append("  ");
        if (clean(rx.frequency()) != null) sb.append(rx.frequency().trim()).append("  ");
        if (clean(rx.duration()) != null) sb.append("for ").append(rx.duration().trim());
        String d = sb.toString().trim();
        if (clean(rx.instructions()) != null) d = (d.isEmpty() ? "" : d + ". ") + rx.instructions().trim();
        return d;
    }

    /**
     * The prescription on Document Studio. Same content as the earlier PDF, without the "Dispensed" box: whether it has been dispensed
     * changes after issue and is not part of the script. {@code signed} false prints a not-issued notice and is never numbered.
     */
    public static Built prescription(Letterhead head, Person patient, Prescriber prescriber, LocalDate visitDate, List<RxLine> lines,
                                     boolean signed, LocalDate issuedOn) {
        if (patient == null) throw new IllegalArgumentException("A prescription needs a patient");
        List<RxLine> rx = lines == null ? List.of() : lines;
        List<Block> b = new ArrayList<>();
        b.add(new Heading(1, "Prescription"));
        b.add(new TextBlock("Date issued: " + fmt(issuedOn), false, true));
        if (!signed) b.add(new TextBlock(NOT_SIGNED, true, false));
        if (prescriber != null) {
            List<String[]> pr = new ArrayList<>();
            pr.add(new String[]{"Name", drName(prescriber.name())});
            if (clean(prescriber.hpcsa()) != null) pr.add(new String[]{"HPCSA no.", prescriber.hpcsa().trim()});
            if (clean(prescriber.practiceNumber()) != null) pr.add(new String[]{"Practice no.", prescriber.practiceNumber().trim()});
            if (clean(prescriber.specialty()) != null) pr.add(new String[]{"Specialty", prescriber.specialty().trim()});
            b.add(new Heading(2, "Prescriber"));
            b.add(new KeyValues(pr));
        }
        b.add(new Heading(2, "Patient"));
        b.add(new KeyValues(personRows(patient, false)));
        b.add(new Heading(2, "Medicines prescribed"));
        if (rx.isEmpty()) {
            b.add(new TextBlock("No medicines prescribed for this consultation.", false, true));
        } else {
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < rx.size(); i++) {
                RxLine r = rx.get(i);
                String med = r.medicine() == null ? "" : r.medicine().trim();
                if (r.schedule() != null) med += " (Sch " + r.schedule() + ")";
                if (clean(r.nappi()) != null) med += " · NAPPI " + r.nappi().trim();
                rows.add(List.of(String.valueOf(i + 1), med, directions(r), r.quantity() == null ? "—" : String.valueOf(r.quantity()), String.valueOf(r.repeats())));
            }
            b.add(new Grid(List.of("#", "Medicine", "Directions", "Qty", "Repeats"), List.of(0.5, 3.5, 4.5, 1.0, 1.2), rows));
        }
        String line = prescriber == null ? null : String.join(" · ", java.util.stream.Stream.of(
                clean(prescriber.hpcsa()) == null ? null : "HPCSA " + prescriber.hpcsa().trim(),
                clean(prescriber.practiceNumber()) == null ? null : "Practice no. " + prescriber.practiceNumber().trim())
                .filter(java.util.Objects::nonNull).toList());
        b.add(new Signature("Prescriber's signature", prescriber == null ? "Prescriber" : drName(prescriber.name()), clean(line), null));
        b.add(new TextBlock(PRESCRIPTION_VALIDITY, false, true));

        Map<String, Object> s = personSnapshot(patient);
        s.put("visitDate", visitDate == null ? null : visitDate.toString());
        s.put("prescriber", prescriber == null ? null : java.util.Arrays.asList(prescriber.name(), clean(prescriber.hpcsa()), clean(prescriber.practiceNumber()), clean(prescriber.specialty())));
        List<Map<String, Object>> meds = new ArrayList<>();
        for (RxLine r : rx) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("medicine", r.medicine()); m.put("schedule", str(r.schedule())); m.put("nappi", clean(r.nappi()));
            m.put("dosage", clean(r.dosage())); m.put("frequency", clean(r.frequency())); m.put("duration", clean(r.duration()));
            m.put("instructions", clean(r.instructions())); m.put("quantity", str(r.quantity())); m.put("repeats", String.valueOf(r.repeats()));
            meds.add(m);
        }
        s.put("medicines", meds);
        return new Built(new DocumentModel("Prescription", head, b, null, true), s);
    }

    /** Vital signs as recorded, already formatted with their units (blank = not recorded). */
    public record Vitals(String weight, String height, String bloodPressure, String pulse, String temperature, String oxygen) {
        boolean any() { return java.util.stream.Stream.of(weight, height, bloodPressure, pulse, temperature, oxygen).anyMatch(v -> clean(v) != null); }
    }

    /** A note added after signing, as printed: its type, when, who, and the text. */
    public record Addendum(String kind, String when, String author, String text) {}

    public static final String SUMMARY_NOTE = "This summary is provided for the patient's own records. It does not replace advice from your treating practitioner.";

    /** The visit summary (medical report) on Document Studio; same sections as the earlier PDF. */
    public static Built visitSummary(Letterhead head, Person patient, String practitionerName, LocalDate visitDate, Vitals vitals,
                                     String chiefComplaint, String diagnosis, List<String> icd10, String treatmentPlan, Integer followUpDays,
                                     List<Addendum> addenda, boolean signed, LocalDate issuedOn) {
        if (patient == null) throw new IllegalArgumentException("A visit summary needs a patient");
        List<Block> b = new ArrayList<>();
        b.add(new Heading(1, "Visit summary"));
        b.add(new TextBlock("Date issued: " + fmt(issuedOn), false, true));
        if (!signed) b.add(new TextBlock(NOT_SIGNED, true, false));
        List<String[]> who = new ArrayList<>();
        who.add(new String[]{"Patient", patient.fullName()});
        if (patient.dateOfBirth() != null) who.add(new String[]{"Date of birth", fmt(patient.dateOfBirth())});
        who.add(new String[]{"Visit date", fmt(visitDate)});
        if (clean(practitionerName) != null) who.add(new String[]{"Practitioner", drName(practitionerName)});
        b.add(new KeyValues(who));
        if (vitals != null && vitals.any()) {
            b.add(new Heading(2, "Vitals"));
            b.add(new Grid(List.of("Weight", "Height", "Blood pressure", "Pulse", "Temperature", "O2 saturation"), List.of(1.0, 1.0, 1.2, 1.0, 1.1, 1.1),
                    List.of(List.of(dash(vitals.weight()), dash(vitals.height()), dash(vitals.bloodPressure()), dash(vitals.pulse()), dash(vitals.temperature()), dash(vitals.oxygen())))));
        }
        section(b, "Chief complaint", chiefComplaint);
        section(b, "Diagnosis", diagnosis);
        if (icd10 != null && !icd10.isEmpty()) section(b, "ICD-10 codes", String.join(", ", icd10));
        section(b, "Treatment plan", treatmentPlan);
        if (followUpDays != null && followUpDays > 0)
            section(b, "Follow-up", "Follow-up recommended in " + followUpDays + " day" + (followUpDays == 1 ? "" : "s") + ".");
        List<Addendum> notes = addenda == null ? List.of() : addenda;
        if (!notes.isEmpty()) {
            b.add(new Heading(2, "Addenda (added after signing)"));
            for (Addendum a : notes) {
                b.add(new TextBlock(a.kind() + " · " + a.when() + " · " + (clean(a.author()) == null ? "author not recorded" : a.author()), true, false));
                b.add(TextBlock.of(a.text() == null ? "" : a.text()));
            }
        }
        b.add(new TextBlock(SUMMARY_NOTE, false, true));

        Map<String, Object> s = personSnapshot(patient);
        s.put("practitioner", clean(practitionerName));
        s.put("visitDate", visitDate == null ? null : visitDate.toString());
        s.put("signed", String.valueOf(signed));
        if (vitals != null) s.put("vitals", java.util.Arrays.asList(clean(vitals.weight()), clean(vitals.height()), clean(vitals.bloodPressure()),
                clean(vitals.pulse()), clean(vitals.temperature()), clean(vitals.oxygen())));
        s.put("chiefComplaint", clean(chiefComplaint));
        s.put("diagnosis", clean(diagnosis));
        s.put("icd10", icd10 == null ? List.of() : new ArrayList<>(icd10));
        s.put("treatmentPlan", clean(treatmentPlan));
        s.put("followUpDays", str(followUpDays));
        List<List<String>> ad = new ArrayList<>();
        for (Addendum a : notes) ad.add(java.util.Arrays.asList(a.kind(), a.when(), a.author(), a.text()));
        s.put("addenda", ad);
        return new Built(new DocumentModel("Visit summary", head, b, null, true), s);
    }

    private static String dash(String v) { return clean(v) == null ? "—" : v.trim(); }

    /** One consent decision as recorded (patch 0203). Facts only: no consent wording is written by the system. */
    public record ConsentFact(String type, String action, String method, String recordedBy, String recordedAt, String notes) {}

    private static final Map<String, String> CONSENT_LABEL = Map.of(
            "TREATMENT", "Treatment",
            "MEDICAL_AID_SHARING", "Sharing information with the medical aid",
            "THIRD_PARTY_REFERRAL", "Sharing information for a referral",
            "MARKETING", "Marketing messages",
            "RESEARCH", "Research");

    public static String consentLabel(String type) {
        if (type == null) return "—";
        return CONSENT_LABEL.getOrDefault(type.toUpperCase(Locale.ROOT), type.replace('_', ' ').toLowerCase(Locale.ROOT));
    }

    public static String decisionLabel(String action) {
        if (action == null) return "—";
        return switch (action.toUpperCase(Locale.ROOT)) { case "GRANTED" -> "Given"; case "REVOKED" -> "Withdrawn"; default -> action; };
    }

    public static String methodLabel(String method) {
        if (clean(method) == null) return "Not recorded";
        return switch (method.trim().toUpperCase(Locale.ROOT)) { case "VERBAL" -> "Verbal"; case "WRITTEN" -> "Written"; case "ELECTRONIC" -> "Electronic"; default -> method.trim(); };
    }

    public static final String CONSENT_NOTE = "A copy of the consent decision as the practice recorded it on the date shown.";

    /** The consent record: the patient, what the consent covers, the decision, how and when it was recorded, and signature lines. */
    public static Built consentRecord(Letterhead head, Person patient, ConsentFact fact, LocalDate issuedOn) {
        if (patient == null) throw new IllegalArgumentException("A consent record needs a patient");
        if (fact == null) throw new IllegalArgumentException("Choose the consent to print");
        List<Block> b = new ArrayList<>();
        b.add(new Heading(1, "Record of consent"));
        b.add(new TextBlock("Date issued: " + fmt(issuedOn), false, true));
        b.add(new Heading(2, "Patient"));
        b.add(new KeyValues(personRows(patient, false)));
        b.add(new Heading(2, "Consent"));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Consent for", consentLabel(fact.type())});
        rows.add(new String[]{"Decision", decisionLabel(fact.action())});
        rows.add(new String[]{"How it was given", methodLabel(fact.method())});
        rows.add(new String[]{"Recorded by", clean(fact.recordedBy()) == null ? "—" : fact.recordedBy().trim()});
        rows.add(new String[]{"Recorded on", dash(fact.recordedAt())});
        if (clean(fact.notes()) != null) rows.add(new String[]{"Notes", fact.notes().trim()});
        b.add(new KeyValues(rows));
        b.add(new TextBlock(CONSENT_NOTE, false, true));
        b.add(new Signature(null, "Patient or legal guardian", "Name, signature and date", null));
        b.add(new Signature(null, clean(fact.recordedBy()) == null ? "Recorded by" : fact.recordedBy().trim(), "Signature and date", null));

        Map<String, Object> s = personSnapshot(patient);
        s.put("consentType", fact.type()); s.put("decision", fact.action()); s.put("method", clean(fact.method()));
        s.put("recordedBy", clean(fact.recordedBy())); s.put("recordedAt", clean(fact.recordedAt())); s.put("notes", clean(fact.notes()));
        return new Built(new DocumentModel("Record of consent", head, b, null, true), s);
    }

    private static void section(List<Block> b, String title, String text) {
        if (clean(text) == null) return;
        b.add(new Heading(3, title));
        b.add(TextBlock.of(text.trim()));
    }

    private static Signature signature(String closing, Signer signer) {
        return new Signature(closing, signer == null ? "" : signer.name(), signer == null ? null : signer.line(), null);
    }
}
