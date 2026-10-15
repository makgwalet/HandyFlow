package za.co.handyflow.platform.audittrail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.audittrail.application.internal.AuditTrailService;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuditTrailServiceTest {

    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AuditTrailService service = new AuditTrailService(jdbc);

    @Test
    void recordsWhoDidWhatWithBeforeAndAfterAsJson() {
        TenantId t = TenantId.generate();
        UUID auditor = UUID.randomUUID(), item = UUID.randomUUID();
        service.recordAs(t, "AUDITOR", auditor, "A. Auditor", "AUDITOR_VIEWED_EVIDENCE", "EVIDENCE", item,
                Map.of("status", "ACTIVE"), Map.of("status", "DETACHED"));
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        var v = args.getAllValues();
        assertEquals(t.getValue(), v.get(0));
        assertEquals("AUDITOR", v.get(1));
        assertEquals(auditor, v.get(2));
        assertEquals("AUDITOR_VIEWED_EVIDENCE", v.get(4));
        assertEquals("{\"status\":\"ACTIVE\"}", v.get(7));
        assertEquals("{\"status\":\"DETACHED\"}", v.get(8));
    }

    @Test
    void nullBeforeAndAfterAreStoredAsNull() {
        service.recordAs(TenantId.generate(), "SYSTEM", null, null, "X", "TENANT", null, null, null);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        assertNull(args.getAllValues().get(7));
        assertNull(args.getAllValues().get(8));
    }
}
