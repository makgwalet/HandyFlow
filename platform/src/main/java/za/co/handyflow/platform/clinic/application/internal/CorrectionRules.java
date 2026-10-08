package za.co.handyflow.platform.clinic.application.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** What changed when a patient's name, ID number, date of birth or sex was corrected (patch 0176). Pure. */
final class CorrectionRules {

    private CorrectionRules() {}

    record Facts(String firstName, String lastName, String idNumber, LocalDate dateOfBirth, String gender, String sexAtBirth) {}
    record Change(String field, String oldValue, String newValue) {}

    /** One change per field whose value differs; a field that was blank and is still blank is not a change. */
    static List<Change> diff(Facts before, Facts after) {
        List<Change> out = new ArrayList<>();
        add(out, "FIRST_NAME", before.firstName(), after.firstName());
        add(out, "LAST_NAME", before.lastName(), after.lastName());
        add(out, "ID_NUMBER", before.idNumber(), after.idNumber());
        add(out, "DATE_OF_BIRTH", before.dateOfBirth() == null ? null : before.dateOfBirth().toString(),
                after.dateOfBirth() == null ? null : after.dateOfBirth().toString());
        add(out, "GENDER", before.gender(), after.gender());
        add(out, "SEX_AT_BIRTH", before.sexAtBirth(), after.sexAtBirth());
        return out;
    }

    private static void add(List<Change> out, String field, String before, String after) {
        String b = blankToNull(before), a = blankToNull(after);
        if (!Objects.equals(b, a)) out.add(new Change(field, b, a));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
