package za.co.handyflow.platform.fleet.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DrivabilityRulesTest {
    TenantId tenant = TenantId.generate();
    UUID vehicle = UUID.randomUUID();

    Accident accident(String ref, boolean drivable) {
        AccidentDetails d = new AccidentDetails(null, null, null, Instant.now(), "N1", null, null, null, null, null, null, null, null, null,
                "Rear-ended at a robot", null, 0, 0, BigDecimal.TEN, null, drivable);
        return Accident.report(tenant, ref, vehicle, d, UUID.randomUUID());
    }

    @Test void aVehicleThatCanBeDrivenIsNeverBlocked() {
        assertNull(DrivabilityRules.problem(List.of(accident("ACC-1", true))));
        assertNull(DrivabilityRules.problem(List.of()));
    }

    @Test void anOpenUndrivableCaseBlocksAndNamesTheCase() {
        String p = DrivabilityRules.problem(List.of(accident("ACC-2026-00021", false)));
        assertNotNull(p);
        assertTrue(p.contains("ACC-2026-00021"));
    }

    @Test void everyOpenStageBlocks() {
        Accident a = accident("ACC-2", false);
        assertTrue(DrivabilityRules.blocks(a));                       // reported
        a.changeStatus(AccidentStatus.UNDER_ASSESSMENT, "assessor booked");
        assertTrue(DrivabilityRules.blocks(a));
        a.changeStatus(AccidentStatus.IN_REPAIR, "at the panel beater");
        assertTrue(DrivabilityRules.blocks(a));
    }

    @Test void closingTheCaseLiftsTheBlock() {
        Accident a = accident("ACC-3", false);
        a.changeStatus(AccidentStatus.CLOSED, "repaired and returned");
        assertFalse(DrivabilityRules.blocks(a));
        assertNull(DrivabilityRules.problem(List.of(a)));
    }

    @Test void aWriteOffStaysBlocked() {
        Accident a = accident("ACC-4", false);
        a.changeStatus(AccidentStatus.WRITTEN_OFF, "total loss");
        assertTrue(DrivabilityRules.blocks(a));
    }

    @Test void correctingTheCaseToDrivableLiftsTheBlock() {
        Accident a = accident("ACC-5", false);
        AccidentDetails fixed = new AccidentDetails(null, null, null, a.getOccurredAt(), "N1", null, null, null, null, null, null, null, null, null,
                "Minor scrape; checked by the workshop", null, 0, 0, BigDecimal.TEN, null, true);
        a.applyDetails(fixed);
        assertNull(DrivabilityRules.problem(List.of(a)));
    }

    @Test void severalBlockingCasesAreAllListed() {
        String p = DrivabilityRules.problem(List.of(accident("ACC-A", false), accident("ACC-B", true), accident("ACC-C", false)));
        assertTrue(p.contains("ACC-A") && p.contains("ACC-C") && !p.contains("ACC-B"));
    }
}
