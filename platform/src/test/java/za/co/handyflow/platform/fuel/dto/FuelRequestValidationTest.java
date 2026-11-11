package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FuelRequestValidationTest {

    final Validator v = Validation.buildDefaultValidatorFactory().getValidator();
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test @DisplayName("dispatch: zero and negative litres are rejected, positive accepted")
    void dispatch() {
        assertFalse(v.validate(new DispatchFuelRequest(bd("-5"), null, Instant.now(), null, null, null, "X", null, null, null, null)).isEmpty());
        assertFalse(v.validate(new DispatchFuelRequest(bd("0"), null, Instant.now(), null, null, null, "X", null, null, null, null)).isEmpty());
        assertTrue(v.validate(new DispatchFuelRequest(bd("50"), bd("22.1"), Instant.now(), null, null, null, "X", null, null, null, null)).isEmpty());
        assertFalse(v.validate(new DispatchFuelRequest(bd("50"), bd("-1"), Instant.now(), null, null, null, "X", null, null, null, null)).isEmpty());
    }

    @Test @DisplayName("receive: litres and price must be positive")
    void receive() {
        assertFalse(v.validate(new ReceiveFuelRequest(bd("-1"), bd("20"), Instant.now(), null, null, null)).isEmpty());
        assertFalse(v.validate(new ReceiveFuelRequest(bd("10"), bd("0"), Instant.now(), null, null, null)).isEmpty());
        assertTrue(v.validate(new ReceiveFuelRequest(bd("10"), bd("20"), Instant.now(), null, null, null)).isEmpty());
    }

    @Test @DisplayName("complete delivery: litres must be positive")
    void complete() {
        assertFalse(v.validate(new CompleteDeliveryRequest(bd("-1"), "R", null, null, null, false, null, null)).isEmpty());
        assertTrue(v.validate(new CompleteDeliveryRequest(bd("1"), "R", null, null, null, false, null, null)).isEmpty());
    }

    @Test @DisplayName("schedule delivery: litres must be positive")
    void schedule() {
        assertFalse(v.validate(new CreateDeliveryRequest(UUID.randomUUID(), null, Map.of(), "DIESEL", bd("0"), bd("22"), Instant.now(), null, null)).isEmpty());
        assertTrue(v.validate(new CreateDeliveryRequest(UUID.randomUUID(), null, Map.of(), "DIESEL", bd("500"), bd("22"), Instant.now(), null, null)).isEmpty());
    }

    @Test @DisplayName("tank: capacity must be positive; threshold 0-100")
    void tank() {
        assertFalse(v.validate(new CreateTankRequest("T", "DIESEL", bd("0"), null, null)).isEmpty());
        assertFalse(v.validate(new CreateTankRequest("T", "DIESEL", bd("1000"), null, bd("150"))).isEmpty());
        assertTrue(v.validate(new CreateTankRequest("T", "DIESEL", bd("1000"), null, bd("20"))).isEmpty());
    }

    @Test @DisplayName("dip reading cannot be negative")
    void dip() {
        assertFalse(v.validate(new DipReadingRequest(bd("-1"), Instant.now(), null, null)).isEmpty());
        assertTrue(v.validate(new DipReadingRequest(bd("0"), Instant.now(), null, null)).isEmpty());
    }

    @Test @DisplayName("reversal needs a reason of 5 to 500 characters")
    void reverse() {
        assertFalse(v.validate(new ReverseRequest(null)).isEmpty());
        assertFalse(v.validate(new ReverseRequest("abc")).isEmpty());
        assertTrue(v.validate(new ReverseRequest("Captured twice")).isEmpty());
        assertFalse(v.validate(new ReverseRequest("x".repeat(501))).isEmpty());
    }

    @Test @DisplayName("dispatch-delivery needs driver and vehicle")
    void dispatchDelivery() {
        assertFalse(v.validate(new DispatchDeliveryRequest("", "CA 1")).isEmpty());
        assertTrue(v.validate(new DispatchDeliveryRequest("Sipho", "CA 123 456")).isEmpty());
    }
}
