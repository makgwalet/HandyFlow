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
        return s.toLowerCase(Locale.ROOT).startsWith("dr") ? s : "Dr. " + s;
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

    private static void section(List<Block> b, String title, String text) {
        if (clean(text) == null) return;
        b.add(new Heading(3, title));
        b.add(TextBlock.of(text.trim()));
    }

    private static Signature signature(String closing, Signer signer) {
        return new Signature(closing, signer == null ? "" : signer.name(), signer == null ? null : signer.line(), null);
    }
}
