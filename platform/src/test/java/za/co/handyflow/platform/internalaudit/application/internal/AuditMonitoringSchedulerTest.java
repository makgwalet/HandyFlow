package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditUniverseEntryRepository;
import za.co.handyflow.platform.internalaudit.dto.AuditMonitoringDtos.ScanResult;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditMonitoringSchedulerTest {
    AuditUniverseEntryRepository universe = mock(AuditUniverseEntryRepository.class);
    AuditMonitoringService monitoring = mock(AuditMonitoringService.class);
    AuditMonitoringScheduler scheduler = new AuditMonitoringScheduler(universe, monitoring);
    LocalDate today = LocalDate.parse("2026-10-14");

    @Test
    void oneCompanyFailingDoesNotStopTheOthers() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        when(universe.findTenantIdsWithAudit()).thenReturn(List.of(a, b, c));
        when(monitoring.scan(eq(TenantId.of(a)), eq(today), any())).thenReturn(new ScanResult(2, 2, 0, Map.of(), 0, 0, 0));
        when(monitoring.scan(eq(TenantId.of(b)), eq(today), any())).thenThrow(new RuntimeException("boom"));
        when(monitoring.scan(eq(TenantId.of(c)), eq(today), any())).thenReturn(new ScanResult(1, 1, 0, Map.of(), 0, 0, 0));
        assertEquals(3, scheduler.sweep(today));
        verify(monitoring, times(3)).scan(any(), eq(today), any());
    }
}
