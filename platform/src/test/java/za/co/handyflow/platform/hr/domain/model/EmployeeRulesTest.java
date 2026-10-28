package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void readsTheFactsInAValidId() {
        EmployeeRules.IdInfo male = EmployeeRules.parseId("8001015009087", TODAY);
        assertThat(male.dateOfBirth()).isEqualTo(LocalDate.of(1980, 1, 1));
        assertThat(male.gender()).isEqualTo("MALE");
        assertThat(male.citizen()).isTrue();
        EmployeeRules.IdInfo female = EmployeeRules.parseId("9002282000089", TODAY);
        assertThat(female.dateOfBirth()).isEqualTo(LocalDate.of(1990, 2, 28));
        assertThat(female.gender()).isEqualTo("FEMALE");
    }

    @Test
    void choosesTheRightCentury() {
        assertThat(EmployeeRules.parseId("0503151230086", TODAY).dateOfBirth()).isEqualTo(LocalDate.of(2005, 3, 15));
        assertThat(EmployeeRules.parseId("5601205001084", TODAY).dateOfBirth()).isEqualTo(LocalDate.of(1956, 1, 20));
    }

    @Test
    void ignoresSpacesInTheNumber() {
        assertThat(EmployeeRules.parseId("800101 5009 087", TODAY)).isNotNull();
        assertThat(EmployeeRules.normaliseId(" 8001 0150 09087 ")).isEqualTo("8001015009087");
    }

    @Test
    void refusesWrongIds() {
        assertThat(EmployeeRules.idProblem("8001015009088", TODAY)).contains("not a valid South African ID");
        assertThat(EmployeeRules.idProblem("80010150090", TODAY)).contains("13 digits");
        assertThat(EmployeeRules.idProblem("8013325009087", TODAY)).contains("not a valid");   // month 13
        assertThat(EmployeeRules.idProblem("abcdefghijklm", TODAY)).contains("13 digits");
    }

    @Test
    void aBlankIdIsAllowed() {
        assertThat(EmployeeRules.idProblem(null, TODAY)).isNull();
        assertThat(EmployeeRules.idProblem("  ", TODAY)).isNull();
        assertThat(EmployeeRules.idProblem("8001015009087", TODAY)).isNull();
    }

    @Test
    void theDateOfBirthMustAgreeWithTheId() {
        assertThat(EmployeeRules.dobProblem("8001015009087", LocalDate.of(1980, 1, 1), LocalDate.of(2024, 1, 1), TODAY)).isNull();
        assertThat(EmployeeRules.dobProblem("8001015009087", LocalDate.of(1980, 1, 2), LocalDate.of(2024, 1, 1), TODAY)).contains("does not match the ID");
        assertThat(EmployeeRules.dobProblem(null, LocalDate.of(1980, 1, 2), LocalDate.of(2024, 1, 1), TODAY)).isNull();
    }

    @Test
    void anEmployeeMustBeOldEnoughAndBornInThePast() {
        assertThat(EmployeeRules.dobProblem(null, LocalDate.of(2015, 1, 1), LocalDate.of(2026, 1, 1), TODAY)).contains("younger than 15");
        assertThat(EmployeeRules.dobProblem(null, LocalDate.of(2011, 1, 1), LocalDate.of(2026, 1, 1), TODAY)).isNull();
        assertThat(EmployeeRules.dobProblem(null, TODAY.plusDays(1), TODAY, TODAY)).contains("future");
    }

    @Test
    void taxEmailAndPhoneAreChecked() {
        assertThat(EmployeeRules.taxNumberProblem("0123456789")).isNull();
        assertThat(EmployeeRules.taxNumberProblem("12345")).contains("10 digits");
        assertThat(EmployeeRules.taxNumberProblem("")).isNull();
        assertThat(EmployeeRules.emailProblem("thandi@example.co.za")).isNull();
        assertThat(EmployeeRules.emailProblem("thandi@")).contains("email");
        assertThat(EmployeeRules.phoneProblem("+27 82 123 4567")).isNull();
        assertThat(EmployeeRules.phoneProblem("abc")).contains("phone");
    }

    @Test
    void bankDetailsGoTogether() {
        assertThat(EmployeeRules.bankProblem(null, null, null)).isNull();
        assertThat(EmployeeRules.bankProblem("FNB", "62012345678", "250655")).isNull();
        assertThat(EmployeeRules.bankProblem(null, "62012345678", null)).contains("bank name");
        assertThat(EmployeeRules.bankProblem("FNB", null, "250655")).contains("account number");
        assertThat(EmployeeRules.bankProblem("FNB", "12AB", null)).contains("4 to 17 digits");
        assertThat(EmployeeRules.bankProblem("FNB", "62012345678", "25065")).contains("6 digits");
    }

    @Test
    void aTerminationNeedsAPastLastDayAndAReason() {
        LocalDate start = LocalDate.of(2024, 3, 1);
        assertThat(EmployeeRules.terminationProblem("ACTIVE", start, LocalDate.of(2026, 10, 15), "Resigned", TODAY)).isNull();
        assertThat(EmployeeRules.terminationProblem("ACTIVE", start, TODAY, "Resigned", TODAY)).isNull();
        assertThat(EmployeeRules.terminationProblem("ACTIVE", start, TODAY.plusDays(1), "Resigned", TODAY)).contains("future");
        assertThat(EmployeeRules.terminationProblem("ACTIVE", start, LocalDate.of(2024, 2, 1), "Resigned", TODAY)).contains("before the start");
        assertThat(EmployeeRules.terminationProblem("ACTIVE", start, TODAY, " ", TODAY)).contains("reason");
        assertThat(EmployeeRules.terminationProblem("ACTIVE", start, TODAY, "x".repeat(201), TODAY)).contains("200");
        assertThat(EmployeeRules.terminationProblem("ACTIVE", start, null, "Resigned", TODAY)).contains("last day");
        assertThat(EmployeeRules.terminationProblem("TERMINATED", start, TODAY, "Resigned", TODAY)).contains("already");
    }

    @Test
    void anAccountIsShownWithItsLastFourDigitsOnly() {
        assertThat(EmployeeRules.maskAccount("62012345678")).isEqualTo("••••5678");
        assertThat(EmployeeRules.maskAccount("123")).isEqualTo("••••");
        assertThat(EmployeeRules.maskAccount(" ")).isNull();
        assertThat(EmployeeRules.maskAccount(null)).isNull();
    }

    @Test
    void raceIsOptionalAndStoredInUpperCase() {
        assertThat(EmployeeRules.raceProblem(null)).isNull();
        assertThat(EmployeeRules.raceProblem("")).isNull();
        assertThat(EmployeeRules.raceProblem("Coloured")).isNull();
        assertThat(EmployeeRules.raceProblem("martian")).contains("Race must be");
        assertThat(EmployeeRules.normaliseRace(" white ")).isEqualTo("WHITE");
        assertThat(EmployeeRules.normaliseRace("  ")).isNull();
    }

    @Test
    void anEmergencyRelationNeedsAContactAndStaysShort() {
        assertThat(EmployeeRules.relationProblem(null, null)).isNull();
        assertThat(EmployeeRules.relationProblem("Sipho", "Brother")).isNull();
        assertThat(EmployeeRules.relationProblem(null, "Brother")).contains("name");
        assertThat(EmployeeRules.relationProblem("Sipho", "x".repeat(51))).contains("too long");
    }

    @Test
    void aPassportNeedsItsNumberAndCountry() {
        assertThat(EmployeeRules.passportProblem(null, null, null, TODAY)).isNull();
        assertThat(EmployeeRules.passportProblem("A1234567", "ZW", null, TODAY)).isNull();
        assertThat(EmployeeRules.passportProblem("ab 123-456", "mw", LocalDate.of(2031, 3, 1), TODAY)).isNull();
        assertThat(EmployeeRules.passportProblem(null, "ZW", null, TODAY)).contains("passport number");
        assertThat(EmployeeRules.passportProblem("A1234567", null, null, TODAY)).contains("country");
        assertThat(EmployeeRules.passportProblem("A1234567", "ZZ", null, TODAY)).contains("two-letter");
        assertThat(EmployeeRules.passportProblem("A1234567", "ZIM", null, TODAY)).contains("two-letter");
        assertThat(EmployeeRules.passportProblem("A12", "ZW", null, TODAY)).contains("5 to 20");
        assertThat(EmployeeRules.passportProblem("A".repeat(21), "ZW", null, TODAY)).contains("5 to 20");
        assertThat(EmployeeRules.passportProblem("A12345*7", "ZW", null, TODAY)).contains("letters and digits");
        assertThat(EmployeeRules.passportProblem("A1234567", "ZW", LocalDate.of(2060, 1, 1), TODAY)).contains("expiry");
    }

    @Test
    void aPassportIsStoredWithoutSpacesAndInUpperCase() {
        assertThat(EmployeeRules.normalisePassport(" ab 123-456 ")).isEqualTo("AB123456");
        assertThat(EmployeeRules.normalisePassport("  ")).isNull();
        assertThat(EmployeeRules.normaliseCountry(" zw ")).isEqualTo("ZW");
        assertThat(EmployeeRules.normaliseCountry("")).isNull();
    }

    @Test
    void aWorkPermitNeedsItsTypeNumberAndUnlessPermanentAnExpiry() {
        LocalDate later = LocalDate.of(2028, 5, 1);
        assertThat(EmployeeRules.permitProblem(null, null, null, TODAY)).isNull();
        assertThat(EmployeeRules.permitProblem("CRITICAL_SKILLS", "VISA-123", later, TODAY)).isNull();
        assertThat(EmployeeRules.permitProblem("PERMANENT_RESIDENCE", "PR-9", null, TODAY)).isNull();
        assertThat(EmployeeRules.permitProblem(null, "VISA-123", null, TODAY)).contains("kind of work permit");
        assertThat(EmployeeRules.permitProblem(null, null, later, TODAY)).contains("kind of work permit");
        assertThat(EmployeeRules.permitProblem("MAGIC", "VISA-123", later, TODAY)).contains("not one HR offers");
        assertThat(EmployeeRules.permitProblem("GENERAL_WORK", " ", later, TODAY)).contains("number");
        assertThat(EmployeeRules.permitProblem("GENERAL_WORK", "VISA-123", null, TODAY)).contains("expires");
        assertThat(EmployeeRules.permitProblem("GENERAL_WORK", "x".repeat(41), later, TODAY)).contains("too long");
        assertThat(EmployeeRules.permitProblem("GENERAL_WORK", "VISA-123", LocalDate.of(2060, 1, 1), TODAY)).contains("expiry");
    }

    @Test
    void aPermitThatEndsBeforeTheStartDateIsRefused() {
        assertThat(EmployeeRules.permitStartProblem(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))).contains("before the start date");
        assertThat(EmployeeRules.permitStartProblem(LocalDate.of(2027, 11, 1), LocalDate.of(2026, 12, 1))).isNull();
        assertThat(EmployeeRules.permitStartProblem(null, LocalDate.of(2026, 12, 1))).isNull();
        assertThat(EmployeeRules.permitStartProblem(LocalDate.of(2026, 11, 1), null)).isNull();
    }

    @Test
    void aPersonIsIdentifiedByAValidIdOrAPassportWithItsCountry() {
        assertThat(EmployeeRules.identified("8001015009087", null, null, TODAY)).isTrue();
        assertThat(EmployeeRules.identified(null, "A1234567", "ZW", TODAY)).isTrue();
        assertThat(EmployeeRules.identified("1234", "A1234567", "ZW", TODAY)).isTrue();
        assertThat(EmployeeRules.identified("1234", null, null, TODAY)).isFalse();
        assertThat(EmployeeRules.identified(null, "A1234567", null, TODAY)).isFalse();
        assertThat(EmployeeRules.identified(null, null, null, TODAY)).isFalse();
    }

    @Test
    void everyPermitTypeHasAPlainName() {
        assertThat(EmployeeRules.PERMIT_TYPES.containsKey("PERMANENT_RESIDENCE")).isTrue();
        assertThat(EmployeeRules.PERMIT_TYPES.values().stream().allMatch(v -> v != null && !v.isBlank())).isTrue();
    }
}
