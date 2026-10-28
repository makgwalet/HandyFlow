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
}
