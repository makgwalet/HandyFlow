package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.WorkingHoursRules.Window;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.clinic.dto.WorkingHoursDtos.SaveWorkingHoursRequest;
import za.co.handyflow.platform.clinic.dto.WorkingHoursDtos.WindowDto;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Time;
import java.util.List;
import java.util.UUID;

/** A practitioner's usual week. No saved hours means no restriction. Saving replaces the whole week. */
@Service
@RequiredArgsConstructor
public class ClinicWorkingHoursService {

    private final JdbcTemplate jdbc;
    private final ClinicPractitionerRepository practitionerRepo;

    @Transactional(readOnly = true)
    public List<WindowDto> get(TenantId t, UUID practitionerId) {
        return windows(t, practitionerId).stream()
                .map(w -> new WindowDto(w.dayOfWeek(), WorkingHoursRules.format(w.from()), WorkingHoursRules.format(w.to())))
                .toList();
    }

    @Transactional
    public List<WindowDto> replace(TenantId t, UUID practitionerId, SaveWorkingHoursRequest req) {
        practitionerRepo.findActiveById(t, practitionerId)
                .orElseThrow(() -> new ResourceNotFoundException("Practitioner", practitionerId.toString()));
        List<Window> week = WorkingHoursRules.validate(req.windows().stream()
                .map(w -> new Window(w.dayOfWeek(), WorkingHoursRules.parse(w.from()), WorkingHoursRules.parse(w.to())))
                .toList());
        jdbc.update("DELETE FROM clinic_practitioner_working_hours WHERE tenant_id = ? AND practitioner_id = ?",
                t.getValue(), practitionerId);
        UUID by = currentUserIdOrNull();
        for (Window w : week) {
            jdbc.update("INSERT INTO clinic_practitioner_working_hours (id, tenant_id, practitioner_id, day_of_week, from_time, to_time, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), t.getValue(), practitionerId, w.dayOfWeek(), Time.valueOf(w.from()), Time.valueOf(w.to()), by);
        }
        return get(t, practitionerId);
    }

    /** Used when booking. Empty means the practitioner is not restricted. */
    @Transactional(readOnly = true)
    List<Window> windows(TenantId t, UUID practitionerId) {
        return jdbc.query("SELECT day_of_week, from_time, to_time FROM clinic_practitioner_working_hours "
                        + "WHERE tenant_id = ? AND practitioner_id = ? ORDER BY day_of_week, from_time",
                (rs, i) -> new Window(rs.getInt("day_of_week"), rs.getTime("from_time").toLocalTime(), rs.getTime("to_time").toLocalTime()),
                t.getValue(), practitionerId);
    }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
