package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Hold;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Override;
import za.co.handyflow.platform.agriculture.dto.AnimalResponse;
import za.co.handyflow.platform.agriculture.dto.HarvestRecordResponse;
import za.co.handyflow.platform.agriculture.dto.MovementRecordResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A reason to go ahead during a hold is returned as data (reason, held-until day) so it can be stored in its own fields. */
class AgWithdrawalOverrideTest {

    private static final LocalDate TREATED = LocalDate.of(2026, 12, 1);
    private static final Optional<Hold> HELD = Optional.of(new Hold(TREATED, TREATED.plusDays(14), "Terramycin"));

    @Test void nothingHeldMeansNoOverride() {
        assertTrue(AgWithdrawalRules.requireClearOrOverride(Optional.empty(), "Bessie", "withdrawal period", "sold", null).isEmpty());
        assertTrue(AgWithdrawalRules.requireClearOrOverride(Optional.empty(), "Bessie", "withdrawal period", "sold", "ignored").isEmpty());
    }

    @Test void heldWithAReasonReturnsTheReasonAndTheClearDay() {
        Override o = AgWithdrawalRules.requireClearOrOverride(HELD, "Bessie", "withdrawal period", "sold", "  sold for breeding  ").get();
        assertEquals("sold for breeding", o.reason());
        assertEquals(TREATED.plusDays(14), o.heldUntil());
    }

    @Test void heldWithoutAReasonOrWithATooShortOneIsRefused() {
        assertThrows(IllegalStateException.class, () -> AgWithdrawalRules.requireClearOrOverride(HELD, "Bessie", "withdrawal period", "sold", null));
        assertThrows(IllegalStateException.class, () -> AgWithdrawalRules.requireClearOrOverride(HELD, "Bessie", "withdrawal period", "sold", "  ok"));
        assertThrows(IllegalStateException.class, () -> AgWithdrawalRules.requireClearOrOverride(HELD, "Bessie", "withdrawal period", "sold", ""));
    }

    @Test void theMessageStillNamesTheTreatmentAndTheDay() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AgWithdrawalRules.requireClearOrOverride(HELD, "Bessie", "withdrawal period", "sold or slaughtered", null));
        assertTrue(e.getMessage().contains("Terramycin") && e.getMessage().contains("15 Dec 2026"), e.getMessage());
    }

    @Test void theThreeResponsesCarryTheOverrideAndOldCallersStillBuild() {
        UUID id = UUID.randomUUID();
        MovementRecordResponse m = new MovementRecordResponse(id, id, null, TREATED, "SALE", null, null, null, null, null, null, null, Instant.now(),
                "B", null, null, null, null, "sold for breeding", TREATED.plusDays(14));
        assertEquals("sold for breeding", m.withdrawalOverrideReason());
        assertNull(new MovementRecordResponse(id, id, null, TREATED, "SALE", null, null, null, null, null, null, null, Instant.now()).withdrawalOverrideReason());
        MovementRecordResponse noOverride = new MovementRecordResponse(id, id, null, TREATED, "SALE", null, null, null, null, null, null, null, Instant.now(), "B", null, null, null, null);
        assertNull(noOverride.withdrawalHeldUntil());
        assertNull(noOverride.withdrawalOverrideReason());
        assertEquals("B", noOverride.buyerName());

        HarvestRecordResponse h = new HarvestRecordResponse(id, id, TREATED, BigDecimal.TEN, "t", null, null, null, null, null, null, null, Instant.now(),
                "before rain", TREATED.plusDays(7));
        assertEquals(TREATED.plusDays(7), h.withdrawalHeldUntil());
        assertNull(new HarvestRecordResponse(id, id, TREATED, BigDecimal.TEN, "t", null, null, null, null, null, null, null, Instant.now()).withdrawalOverrideReason());

        AnimalResponse a = new AnimalResponse(id, id, null, null, id, "T-1", "Bella", null, null, null, false, null, null, "BORN_ON_FARM", null, null, null,
                "SOLD", null, Instant.now(), Instant.now(), false, "urgent", TREATED);
        assertEquals("urgent", a.withdrawalOverrideReason());
        assertNull(new AnimalResponse(id, id, null, null, id, "T-1", "Bella", null, null, null, false, null, null, "BORN_ON_FARM", null, null, null,
                "SOLD", null, Instant.now(), Instant.now(), false).withdrawalOverrideReason());
    }
}
