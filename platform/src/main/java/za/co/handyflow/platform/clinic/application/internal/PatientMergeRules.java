package za.co.handyflow.platform.clinic.application.internal;

import java.time.LocalDate;
import java.util.*;

/**
 * Rules for merging two patient records that are the same person (patch 0188). Pure.
 * The survivor keeps its own identity; the other record is archived, not deleted, and everything attached to it moves across.
 */
final class PatientMergeRules {
    private PatientMergeRules() {}

    /** What the database says about one of the two records. */
    record Side(UUID id, boolean found, boolean archived, UUID mergedInto, String patientNumber, String firstName, String lastName,
                String idNumber, LocalDate dateOfBirth, String sexAtBirth, boolean restricted, int openVisits) {}

    record Conflict(String field, String text, boolean blocking) {}

    enum Handling { MOVE, SINGLE, SKIP }

    /** Tables that keep a trail of who looked at or changed what: they stay as they were, tied to the record they were written for. */
    private static final Set<String> TRAIL = Set.of("clinic_access_log", "clinic_break_glass_audit", "clinic_break_glass_sessions",
            "clinic_patient_corrections", "clinic_restricted_records", "clinic_patient_merges");
    /** Tables with one row per patient (the patient is the key): the survivor's row wins; the other stays with the archived record. */
    private static final Set<String> ONE_PER_PATIENT = Set.of("clinic_patient_profile", "clinic_patient_social_history");

    static Handling handling(String table) {
        if (TRAIL.contains(table)) return Handling.SKIP;
        if (ONE_PER_PATIENT.contains(table)) return Handling.SINGLE;
        return Handling.MOVE;
    }

    /** Columns that may be copied from the archived record into the survivor when the survivor has nothing there. */
    static final List<String> FILLABLE = List.of("id_number", "date_of_birth", "gender", "sex_at_birth", "phone", "email",
            "blood_type", "emergency_contact_name", "emergency_contact_phone");

    /** Which of the fillable columns the survivor lacks and the other record has. Values are compared as text; blank counts as nothing. */
    static List<String> fill(Map<String, String> survivor, Map<String, String> other) {
        List<String> out = new ArrayList<>();
        for (String c : FILLABLE) if (blank(survivor.get(c)) && !blank(other.get(c))) out.add(c);
        return out;
    }

    /** Reasons this pair cannot be merged right now. Empty means it can. */
    static List<String> blockers(Side survivor, Side other) {
        List<String> out = new ArrayList<>();
        if (survivor.id() != null && survivor.id().equals(other.id())) { out.add("Choose two different patients."); return out; }
        if (!survivor.found()) out.add("The record to keep was not found.");
        if (!other.found()) out.add("The record to merge away was not found.");
        if (!out.isEmpty()) return out;
        if (survivor.archived()) out.add("The record to keep is archived" + (survivor.mergedInto() != null ? " (it was already merged into another patient)" : "") + ".");
        if (other.archived()) out.add("The record to merge away is archived" + (other.mergedInto() != null ? " (it was already merged into another patient)" : "") + ".");
        if (survivor.restricted() || other.restricted()) out.add("A restricted record cannot be merged here. Release the restriction first.");
        if (other.openVisits() > 0) out.add("The record to merge away has " + other.openVisits() + " visit" + (other.openVisits() == 1 ? "" : "s")
                + " still open. Sign or discard " + (other.openVisits() == 1 ? "it" : "them") + " first.");
        if (survivor.openVisits() > 0 && other.openVisits() == 0) { /* the survivor's own open visits are untouched */ }
        return out;
    }

    /** Where the two records disagree. Blocking ones need the person merging to say they have checked. */
    static List<Conflict> conflicts(Side s, Side o) {
        List<Conflict> out = new ArrayList<>();
        String a = compact(s.idNumber()), b = compact(o.idNumber());
        if (!a.isEmpty() && !b.isEmpty() && !a.equals(b)) out.add(new Conflict("ID number", "ID numbers differ: " + a + " and " + b, true));
        if (s.dateOfBirth() != null && o.dateOfBirth() != null && !s.dateOfBirth().equals(o.dateOfBirth()))
            out.add(new Conflict("Date of birth", "Dates of birth differ: " + s.dateOfBirth() + " and " + o.dateOfBirth(), true));
        if (!blank(s.sexAtBirth()) && !blank(o.sexAtBirth()) && !s.sexAtBirth().equalsIgnoreCase(o.sexAtBirth()))
            out.add(new Conflict("Sex at birth", "Sex at birth differs: " + s.sexAtBirth() + " and " + o.sexAtBirth(), false));
        if (!same(s.firstName(), o.firstName()) || !same(s.lastName(), o.lastName()))
            out.add(new Conflict("Name", "Names differ: " + name(s) + " and " + name(o), false));
        return out;
    }

    static boolean needsAcknowledgement(List<Conflict> conflicts) { return conflicts.stream().anyMatch(Conflict::blocking); }

    /** The reason typed by the person merging: required, so the record says why. */
    static String reason(String raw) {
        String r = raw == null ? "" : raw.trim();
        if (r.length() < 5) throw new IllegalArgumentException("Say why these records are being merged (at least 5 characters).");
        if (r.length() > 500) throw new IllegalArgumentException("Keep the reason under 500 characters.");
        return r;
    }

    static String archiveReason(String survivorNumber, String survivorName) {
        return "Merged into " + (blank(survivorNumber) ? "" : survivorNumber + " ") + survivorName;
    }

    /** A readable name for a table in the preview. */
    static String label(String table) {
        return switch (table) {
            case "clinic_appointments" -> "Appointments";
            case "clinic_consultations" -> "Visits";
            case "clinic_prescriptions" -> "Prescriptions";
            case "clinic_claims" -> "Claims";
            case "clinic_payments" -> "Payments";
            case "clinic_lab_results" -> "Lab results";
            case "clinic_observations" -> "Measurements";
            case "clinic_medical_aids" -> "Medical aid";
            case "clinic_patient_allergies" -> "Allergies";
            case "clinic_patient_conditions" -> "Conditions";
            case "clinic_patient_medications" -> "Medicines on record";
            case "clinic_patient_notes" -> "Notes";
            case "clinic_patient_documents" -> "Documents";
            case "clinic_patient_consents" -> "Consents";
            case "clinic_patient_family_history" -> "Family history";
            case "clinic_tasks" -> "Tasks";
            case "clinic_recall_actions" -> "Recalls";
            case "clinic_waitlist_entries" -> "Waiting list";
            case "clinic_patient_profile" -> "Profile";
            case "clinic_patient_social_history" -> "Lifestyle and social history";
            default -> {
                String t = table.startsWith("clinic_") ? table.substring(7) : table;
                String s = t.replace('_', ' ');
                yield s.isEmpty() ? table : Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }

    /** Only plain table names are ever put into SQL. */
    static boolean safeName(String table) { return table != null && table.matches("[a-z][a-z0-9_]{0,62}"); }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String compact(String s) { return s == null ? "" : s.replaceAll("\\s+", ""); }
    private static boolean same(String a, String b) { return (a == null ? "" : a.trim()).equalsIgnoreCase(b == null ? "" : b.trim()); }
    private static String name(Side s) { return ((s.firstName() == null ? "" : s.firstName()) + " " + (s.lastName() == null ? "" : s.lastName())).trim(); }
}
