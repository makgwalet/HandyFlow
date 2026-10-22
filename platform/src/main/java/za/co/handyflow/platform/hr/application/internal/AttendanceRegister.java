package za.co.handyflow.platform.hr.application.internal;

import za.co.handyflow.platform.hr.dto.AttendanceDtos.RegisterRow;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The attendance register for a period, as far as the person asking may see it. The reports use this and nothing else of attendance. */
public interface AttendanceRegister {
    List<RegisterRow> register(TenantId tenantId, LocalDate from, LocalDate to, Set<String> held, UUID actor);
}
