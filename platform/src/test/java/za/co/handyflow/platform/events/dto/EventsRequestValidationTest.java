package za.co.handyflow.platform.events.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bad input must come back as a 400 naming the field, never reach the database and fail as a 500. The limits here
 * are the column widths and CHECK constraints in the events migrations.
 */
class EventsRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    private static final LocalDateTime START = LocalDateTime.of(2037, 1, 10, 18, 0);

    private static <T> Set<String> fieldsWithProblems(T request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    private static String repeat(int n) {
        return "x".repeat(n);
    }

    private CreateEventRequest event(String title, String type, Integer capacity) {
        return new CreateEventRequest(title, null, type, null, null, capacity, START, START.plusHours(4),
                false, false, null, null);
    }

    @Test
    void aSensibleEvent_isAccepted() {
        assertThat(fieldsWithProblems(event("Annual Gala", "CONFERENCE", 200))).isEmpty();
        assertThat(fieldsWithProblems(event("Annual Gala", null, null))).isEmpty();
    }

    @Test
    void aTitleLongerThanTheColumn_isRejected() {
        assertThat(fieldsWithProblems(event(repeat(301), "GENERAL", 10))).contains("title");
        assertThat(fieldsWithProblems(event(repeat(300), "GENERAL", 10))).isEmpty();
    }

    @Test
    void anEventTypeTheDatabaseDoesNotKnow_isRejected() {
        assertThat(fieldsWithProblems(event("Gala", "PARTY", 10))).contains("eventType");
        assertThat(fieldsWithProblems(event("Gala", "conference", 10))).contains("eventType");
    }

    @Test
    void zeroOrNegativeCapacity_isRejected() {
        assertThat(fieldsWithProblems(event("Gala", "GENERAL", 0))).contains("venueCapacity");
        assertThat(fieldsWithProblems(event("Gala", "GENERAL", -5))).contains("venueCapacity");
    }

    @Test
    void aTier_cannotHaveANegativePrice_orAnAbsurdQuantity() {
        assertThat(fieldsWithProblems(new CreateTierRequest("VIP", null, new BigDecimal("-1"), 10, null, null)))
                .contains("price");
        assertThat(fieldsWithProblems(new CreateTierRequest("VIP", null, new BigDecimal("100"), 0, null, null)))
                .contains("quantity");
        assertThat(fieldsWithProblems(new CreateTierRequest("VIP", null, new BigDecimal("100"), 5_000_000, null, null)))
                .contains("quantity");
        assertThat(fieldsWithProblems(new CreateTierRequest("VIP", null, new BigDecimal("123456789.00"), 10, null, null)))
                .contains("price");                                  // would overflow NUMERIC(10,2)
        assertThat(fieldsWithProblems(new CreateTierRequest("Free", null, BigDecimal.ZERO, 10, null, null))).isEmpty();
    }

    @Test
    void aGuest_needsAValidEmail_andCannotHaveNegativeMoney() {
        assertThat(fieldsWithProblems(new RegisterGuestRequest(null, null, "Ann", "not-an-email", null, null, null, null, null)))
                .contains("email");
        assertThat(fieldsWithProblems(new RegisterGuestRequest(null, null, "Ann", "ann@example.co.za", null, null, null,
                new BigDecimal("-10"), null))).contains("amountPaid");
        assertThat(fieldsWithProblems(new RegisterGuestRequest(null, null, "Ann", "ann@example.co.za", null, null, null,
                new BigDecimal("250.00"), null))).isEmpty();
    }

    @Test
    void guestTextFields_areBoundedToTheirColumns() {
        assertThat(fieldsWithProblems(new RegisterGuestRequest(null, null, repeat(201), null, null, null, null, null, null)))
                .contains("fullName");
        assertThat(fieldsWithProblems(new RegisterGuestRequest(null, null, "Ann", null, repeat(51), null, null, null, null)))
                .contains("phone");
        assertThat(fieldsWithProblems(new RegisterGuestRequest(null, null, "Ann", null, null, null, repeat(501), null, null)))
                .contains("dietaryRequirements");
    }

    @Test
    void aVendor_mustHaveAKnownType_andAValidEmail() {
        assertThat(fieldsWithProblems(new AddVendorRequest("MAGICIAN", "Acme", null, null, null, null, null, null)))
                .contains("vendorType");
        assertThat(fieldsWithProblems(new AddVendorRequest("CATERING", "Acme", null, null, "bad", null, null, null)))
                .contains("contactEmail");
        assertThat(fieldsWithProblems(new AddVendorRequest("CATERING", "Acme", null, null, "ops@acme.co.za", null,
                new BigDecimal("-1"), null))).contains("quotedAmount");
        assertThat(fieldsWithProblems(new AddVendorRequest("CATERING", "Acme", null, null, "ops@acme.co.za", null,
                new BigDecimal("1500.00"), null))).isEmpty();
    }

    @Test
    void aDoorScan_cannotFailOnALongNote() {
        assertThat(fieldsWithProblems(new CheckInRequest(repeat(101), null, null))).contains("qrCode");
        assertThat(fieldsWithProblems(new CheckInRequest("abc", repeat(201), null))).contains("location");
        assertThat(fieldsWithProblems(new CheckInRequest("abc", null, repeat(101)))).contains("scanDevice");
        assertThat(fieldsWithProblems(new CheckInRequest("abc", "Gate A", "scanner-1"))).isEmpty();
    }

    @Test
    void walkInsAndGroups_cannotRecordNegativePayments() {
        assertThat(fieldsWithProblems(new WalkInRequest(null, "Walk", null, null, null, null, new BigDecimal("-5"), null)))
                .contains("amountPaid");
    }
}
