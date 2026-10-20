package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Year;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccidentReferenceGeneratorTest {

    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AccidentReferenceGenerator generator = new AccidentReferenceGenerator(jdbc);

    @Test void theReferenceIsPrefixedWithTheYearAndPaddedToFiveDigits() {
        TenantId tenant = TenantId.generate();
        int year = Year.now(ZoneId.of("Africa/Johannesburg")).getValue();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(tenant.getValue()), eq(year))).thenReturn(42);
        assertEquals("ACC-" + year + "-00042", generator.next(tenant));
    }

    @Test void theCounterIsBumpedInOneStatement() {
        TenantId tenant = TenantId.generate();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object.class), any(Object.class))).thenReturn(1);
        generator.next(tenant);
        verify(jdbc).queryForObject(contains("ON CONFLICT (tenant_id, year) DO UPDATE"), eq(Integer.class),
                any(Object.class), any(Object.class));
    }

    @Test void aMissingCounterRowIsAnError() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object.class), any(Object.class))).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> generator.next(TenantId.generate()));
    }
}
