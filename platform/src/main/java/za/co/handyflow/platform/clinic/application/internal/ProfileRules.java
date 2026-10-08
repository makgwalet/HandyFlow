package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.domain.model.SaIdNumber;
import za.co.handyflow.platform.clinic.dto.ProfileDtos.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Checks and cleaning of profile details, name and ID corrections, and what makes a profile complete. Pure. */
final class ProfileRules {
    private ProfileRules() {}

    static final Set<String> ID_TYPES = Set.of("SA_ID", "PASSPORT", "OTHER");
    static final Set<String> CONTACTS = Set.of("PHONE", "SMS", "WHATSAPP", "EMAIL");
    static final Set<String> PAYERS = Set.of("MEDICAL_AID", "SELF_PAY");
    static final Set<String> SEX_AT_BIRTH = Set.of("MALE", "FEMALE", "INTERSEX", "UNKNOWN");

    static String text(String v, int max, String field) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        if (s.length() > max) throw new IllegalArgumentException(field + " is longer than " + max + " characters");
        return s;
    }

    static String oneOf(String v, Set<String> allowed, String field) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().toUpperCase().replace(' ', '_');
        if (!allowed.contains(s)) throw new IllegalArgumentException(field + " is not one of " + String.join(", ", allowed.stream().sorted().toList()));
        return s;
    }

    /** South African postal codes are four digits. Blank is allowed. */
    static String postalCode(String v) {
        String s = text(v, 10, "Postal code");
        if (s != null && !s.matches("\\d{4}")) throw new IllegalArgumentException("A postal code is four digits");
        return s;
    }

    static ProfileRequest clean(ProfileRequest r) {
        return new ProfileRequest(text(r.title(), 20, "Title"), oneOf(r.idType(), ID_TYPES, "ID type"), text(r.nationality(), 80, "Nationality"),
                text(r.preferredLanguage(), 40, "Language"), oneOf(r.preferredContact(), CONTACTS, "Preferred contact"),
                text(r.addressLine1(), 150, "Address line 1"), text(r.addressLine2(), 150, "Address line 2"), text(r.suburb(), 80, "Suburb"),
                text(r.city(), 80, "City"), text(r.province(), 40, "Province"), postalCode(r.postalCode()),
                text(r.emergencyRelationship(), 40, "Emergency contact relationship"), text(r.secondaryContactName(), 120, "Second contact name"),
                text(r.secondaryContactPhone(), 30, "Second contact phone"), text(r.secondaryContactRelationship(), 40, "Second contact relationship"),
                oneOf(r.paymentType(), PAYERS, "Payment type"));
    }

    record Demographics(String firstName, String lastName, String idNumber, LocalDate dateOfBirth, String gender, String sexAtBirth) {}

    /**
     * A correction to name, ID and date of birth. A 13-digit value is checked as a South African ID and must agree with the
     * date of birth; anything else (a passport) is kept as typed. When an SA ID is given without a date of birth, the ID's date is used.
     */
    static Demographics demographics(DemographicsRequest r, LocalDate today) {
        String first = text(r.firstName(), 100, "First name");
        String last = text(r.lastName(), 100, "Last name");
        if (first == null) throw new IllegalArgumentException("First name is required");
        if (last == null) throw new IllegalArgumentException("Last name is required");
        String id = text(r.idNumber(), 30, "ID number");
        if (id != null) id = id.replace(" ", "");
        LocalDate dob = r.dateOfBirth();
        if (id != null && SaIdNumber.looksLikeSaId(id)) {
            String problem = SaIdNumber.problem(id);
            if (problem != null) throw new IllegalArgumentException("Invalid ID number: " + problem);
            SaIdNumber parsed = SaIdNumber.parseOrNull(id);
            if (dob != null && !parsed.matchesDateOfBirth(dob)) throw new IllegalArgumentException("The date of birth does not match the ID number.");
            if (dob == null) dob = parsed.dateOfBirth();
        }
        if (dob != null && dob.isAfter(today)) throw new IllegalArgumentException("The date of birth cannot be in the future");
        if (dob != null && dob.isBefore(today.minusYears(130))) throw new IllegalArgumentException("The date of birth is more than 130 years ago");
        return new Demographics(first, last, id, dob, text(r.gender(), 20, "Gender"), oneOf(r.sexAtBirth(), SEX_AT_BIRTH, "Sex at birth"));
    }

    record Contact(String phone, String email, String emergencyName, String emergencyPhone) {}

    static Contact contact(ContactRequest r) {
        String email = text(r.email(), 150, "Email");
        if (email != null && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("That email address does not look right");
        String phone = text(r.phone(), 30, "Phone");
        String ePhone = text(r.emergencyContactPhone(), 30, "Emergency contact phone");
        if (phone != null && phone.replaceAll("\\D", "").length() < 9) throw new IllegalArgumentException("A phone number needs at least 9 digits");
        if (ePhone != null && ePhone.replaceAll("\\D", "").length() < 9) throw new IllegalArgumentException("The emergency contact's phone needs at least 9 digits");
        return new Contact(phone, email, text(r.emergencyContactName(), 120, "Emergency contact name"), ePhone);
    }

    record Facts(String firstName, String lastName, LocalDate dateOfBirth, String sexAtBirth, String idNumber, String phone,
                 String emergencyName, String emergencyPhone, String addressLine1, String city, String paymentType,
                 boolean hasMedicalAid, boolean treatmentConsent) {}

    private static boolean has(String s) { return s != null && !s.isBlank(); }

    static Completeness completeness(Facts f) {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem("name", "Full name", "identity", has(f.firstName()) && has(f.lastName())));
        items.add(new ChecklistItem("dob", "Date of birth", "identity", f.dateOfBirth() != null));
        items.add(new ChecklistItem("sex", "Sex at birth", "identity", has(f.sexAtBirth()) && !"UNKNOWN".equals(f.sexAtBirth())));
        items.add(new ChecklistItem("id", "ID or passport number", "identity", has(f.idNumber())));
        items.add(new ChecklistItem("phone", "Phone number", "contact", has(f.phone())));
        items.add(new ChecklistItem("address", "Home address", "address", has(f.addressLine1()) && has(f.city())));
        items.add(new ChecklistItem("emergency", "Emergency contact", "emergency", has(f.emergencyName()) && has(f.emergencyPhone())));
        items.add(new ChecklistItem("payer", "Medical aid or self-pay", "scheme", f.hasMedicalAid() || "SELF_PAY".equals(f.paymentType())));
        items.add(new ChecklistItem("consent", "Treatment consent", "consent", f.treatmentConsent()));
        int done = (int) items.stream().filter(ChecklistItem::done).count();
        return new Completeness(done, items.size(), Math.round(done * 100f / items.size()), List.copyOf(items));
    }
}
