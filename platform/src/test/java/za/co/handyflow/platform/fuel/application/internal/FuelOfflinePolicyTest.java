package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FuelOfflinePolicyTest {
    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @Test void noCapturedTimeIsFine() { assertDoesNotThrow(() -> FuelOfflinePolicy.checkCaptured(null, NOW)); }
    @Test void exactlyTheMaximumAgeIsAccepted() { assertDoesNotThrow(() -> FuelOfflinePolicy.checkCaptured(NOW.minus(FuelOfflinePolicy.MAX_AGE), NOW)); }
    @Test void aSecondOlderIsRefused() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> FuelOfflinePolicy.checkCaptured(NOW.minus(FuelOfflinePolicy.MAX_AGE).minusSeconds(1), NOW));
        assertTrue(e.getMessage().contains("72 hours"));
    }
    @Test void aPhoneClockFiveMinutesAheadIsAccepted() { assertDoesNotThrow(() -> FuelOfflinePolicy.checkCaptured(NOW.plus(FuelOfflinePolicy.FUTURE_SKEW), NOW)); }
    @Test void furtherAheadIsRefused() { assertThrows(IllegalArgumentException.class, () -> FuelOfflinePolicy.checkCaptured(NOW.plus(FuelOfflinePolicy.FUTURE_SKEW).plusSeconds(1), NOW)); }

    @Test void theCompanysOwnLimitsApply() {
        java.time.Duration day = java.time.Duration.ofHours(24), none = java.time.Duration.ZERO;
        assertDoesNotThrow(() -> FuelOfflinePolicy.checkCaptured(NOW.minus(day), NOW, day, none));
        assertThrows(IllegalStateException.class, () -> FuelOfflinePolicy.checkCaptured(NOW.minus(day).minusSeconds(1), NOW, day, none));
        assertThrows(IllegalArgumentException.class, () -> FuelOfflinePolicy.checkCaptured(NOW.plusSeconds(1), NOW, day, none));
    }
}
