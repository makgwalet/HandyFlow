package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PopiaProcessingActivityTest {

    private final TenantId tenantId = TenantId.generate();

    private PopiaProcessingActivity newActivity(boolean crossBorder, String crossBorderDetails) {
        return PopiaProcessingActivity.create(tenantId, "Payroll processing", DataCategory.EMPLOYEE,
                "Pay employees monthly", LawfulBasis.CONTRACT, "HR", null, null,
                "7 years — Companies Act 71 of 2008 s24", crossBorder, crossBorderDetails,
                "Encrypted at rest", LocalDate.now().plusYears(1), UUID.randomUUID());
    }

    @Test
    @DisplayName("create() starts active")
    void createStartsActive() {
        PopiaProcessingActivity a = newActivity(false, null);
        assertTrue(a.isActive());
    }

    @Test
    @DisplayName("create() requires crossBorderDetails when crossBorderTransfer is true")
    void createRequiresCrossBorderDetailsWhenTrue() {
        assertThrows(IllegalArgumentException.class, () -> newActivity(true, null));
        assertThrows(IllegalArgumentException.class, () -> newActivity(true, "   "));
    }

    @Test
    @DisplayName("create() succeeds with crossBorderTransfer true and details provided")
    void createSucceedsWithCrossBorderDetails() {
        PopiaProcessingActivity a = newActivity(true, "Transferred to AWS eu-west-1 under SCCs");
        assertTrue(a.isCrossBorderTransfer());
        assertEquals("Transferred to AWS eu-west-1 under SCCs", a.getCrossBorderDetails());
    }

    @Test
    @DisplayName("update() also enforces the crossBorderDetails rule")
    void updateEnforcesCrossBorderRule() {
        PopiaProcessingActivity a = newActivity(false, null);
        assertThrows(IllegalArgumentException.class, () -> a.update(
                "Payroll processing", DataCategory.EMPLOYEE, "Pay employees", LawfulBasis.CONTRACT, "HR",
                null, null, "7 years", true, null, "Encrypted", LocalDate.now()));
    }

    @Test
    @DisplayName("deactivate()/reactivate() toggle active")
    void deactivateReactivateToggle() {
        PopiaProcessingActivity a = newActivity(false, null);
        a.deactivate();
        assertFalse(a.isActive());
        a.reactivate();
        assertTrue(a.isActive());
    }

    @Test
    @DisplayName("softDelete() marks the activity deleted")
    void softDeleteMarksDeleted() {
        PopiaProcessingActivity a = newActivity(false, null);
        assertFalse(a.isDeleted());
        a.softDelete(UUID.randomUUID());
        assertTrue(a.isDeleted());
    }
}
