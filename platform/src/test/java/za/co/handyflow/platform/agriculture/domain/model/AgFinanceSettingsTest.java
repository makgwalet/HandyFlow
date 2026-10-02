package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgFinanceSettingsTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final UUID USER = UUID.randomUUID();
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    @DisplayName("new settings keep what they were given and who saved them")
    void creates() {
        AgFinanceSettings s = AgFinanceSettings.create(TENANT, bd("40"), bd("2.35"), USER);
        assertEquals(0, bd("40").compareTo(s.getStandardHoursPerWeek()));
        assertEquals(0, bd("2.35").compareTo(s.getLabourOnCostPercent()));
        assertEquals(USER, s.getUpdatedBy());
        assertNotNull(s.getCreatedAt());
    }

    @Test
    @DisplayName("out-of-range settings are refused")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> AgFinanceSettings.create(null, bd("45"), bd("0"), USER));
        assertThrows(IllegalArgumentException.class, () -> AgFinanceSettings.create(TENANT, bd("0"), bd("0"), USER));
        assertThrows(IllegalArgumentException.class, () -> AgFinanceSettings.create(TENANT, bd("85"), bd("0"), USER));
        assertThrows(IllegalArgumentException.class, () -> AgFinanceSettings.create(TENANT, bd("45"), bd("-1"), USER));
        assertThrows(IllegalArgumentException.class, () -> AgFinanceSettings.create(TENANT, bd("45"), bd("101"), USER));
        assertThrows(IllegalArgumentException.class, () -> AgFinanceSettings.create(TENANT, null, bd("0"), USER));
        assertThrows(IllegalArgumentException.class, () -> AgFinanceSettings.create(TENANT, bd("45"), null, USER));
    }

    @Test
    @DisplayName("update changes both numbers and records who did it")
    void updates() {
        AgFinanceSettings s = AgFinanceSettings.create(TENANT, bd("45"), bd("0"), USER);
        UUID other = UUID.randomUUID();

        s.update(bd("40"), bd("2"), other);

        assertEquals(0, bd("40").compareTo(s.getStandardHoursPerWeek()));
        assertEquals(0, bd("2").compareTo(s.getLabourOnCostPercent()));
        assertEquals(other, s.getUpdatedBy());
    }

    @Test
    @DisplayName("a refused update leaves the settings exactly as they were")
    void refusedUpdateChangesNothing() {
        AgFinanceSettings s = AgFinanceSettings.create(TENANT, bd("45"), bd("2"), USER);

        assertThrows(IllegalArgumentException.class, () -> s.update(bd("100"), bd("2"), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> s.update(bd("40"), bd("150"), UUID.randomUUID()));

        assertEquals(0, bd("45").compareTo(s.getStandardHoursPerWeek()));
        assertEquals(0, bd("2").compareTo(s.getLabourOnCostPercent()));
        assertEquals(USER, s.getUpdatedBy());
    }
}
